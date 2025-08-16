package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * ■핫바 바인딩을 '복사'가 아닌 '슬롯 위임(교체)' 구조로 정리
 * ■기존 코드 호환: saveNBT/loadNBT, getBackpackWidth/Height, getEquipment 등 모두 제공
 * ■클라 동기화: syncUtilityHotbar(), clientSetBinding() 포함
 */
public class PlayerEquipment {

    // === 기존에서 쓰던 상수들 호환 ===
    public static final int EQUIP_SLOTS = 7;
    public static final int SLOT_HEADSET = 0;
    public static final int SLOT_HELMET  = 1;
    public static final int SLOT_VEST    = 2;
    public static final int SLOT_PRIM1   = 3;
    public static final int SLOT_PRIM2   = 4;
    public static final int SLOT_SEC     = 5;
    public static final int SLOT_MELEE   = 6;

    public enum Storage { BASE, BACKPACK }
    public static final int HOTBAR_COUNT = 9;

    // === 인벤토리 ===
    // NOTE: 프로젝트 구조에 맞춰 cap 패키지의 GridItemHandler2D 사용
    private final GridItemHandler2D base2x2 = new GridItemHandler2D(2, 2);
    private GridItemHandler2D backpack = null; // 배낭 장착 시 사이즈에 맞게 생성
    private final ItemStackHandler equipment = new ItemStackHandler(EQUIP_SLOTS);

    // 배낭 '아이템' 자체(겉모습), 크기
    private ItemStack backpackItem = ItemStack.EMPTY;
    private int backpackW = 0, backpackH = 0;

    // === 핫바 바인딩 상태(양방향 매핑) ===
    /** 핫바 -> (저장소, 슬롯) ; storage = -1이면 미바인딩 */
    private final byte[] bindStorage = new byte[HOTBAR_COUNT];
    private final int[]  bindIndex   = new int[HOTBAR_COUNT];

    /** BASE 슬롯 -> 바인딩된 핫바(없으면 -1) */
    private final int[] baseSlotBoundToHotbar = new int[base2x2.getSlots()];
    /** BACKPACK 슬롯 -> 바인딩된 핫바(없으면 -1). 배낭 크기 가변 */
    private int[] bpSlotBoundToHotbar = new int[0];

    // 더티 플래그(기존 코드 호환)
    private boolean dirty = false;

    public PlayerEquipment() {
        for (int i = 0; i < HOTBAR_COUNT; i++) { bindStorage[i] = -1; bindIndex[i] = -1; }
        for (int i = 0; i < baseSlotBoundToHotbar.length; i++) baseSlotBoundToHotbar[i] = -1;
    }

    // ---------- 기존 호환 getter ----------
    public GridItemHandler2D getBase2x2() { return base2x2; }
    /** 일부 코드가 _2D 접미사를 부르는 경우가 있어 호환용으로 유지 */
    public GridItemHandler2D getBase2x2_2D() { return base2x2; }

    public GridItemHandler2D getBackpack()   { return backpack; }
    public GridItemHandler2D getBackpack2D() { return backpack; }

    public ItemStackHandler getEquipment()   { return equipment; }

    public ItemStack getBackpackItem() { return backpackItem; }
    public void setBackpackItem(ItemStack st) { backpackItem = st; dirty = true; }

    public int getBackpackWidth()  { return backpackW; }
    public int getBackpackHeight() { return backpackH; }

    // ---------- 배낭 사이즈 변경 & 재매핑 ----------
    public void resizeBackpack(int w, int h) {
        backpackW = w; backpackH = h;
        backpack = (w > 0 && h > 0) ? new GridItemHandler2D(w, h) : null;
        // 역매핑 테이블 재구성
        int newSize = (backpack == null) ? 0 : backpack.getSlots();
        int[] newMap = new int[newSize];
        for (int i = 0; i < newMap.length; i++) newMap[i] = -1;

        for (int hb = 0; hb < HOTBAR_COUNT; hb++) {
            if (bindStorage[hb] == 1) {
                if (bindIndex[hb] < 0 || bindIndex[hb] >= newSize) {
                    bindStorage[hb] = -1; bindIndex[hb] = -1;
                } else {
                    if (newMap[bindIndex[hb]] != -1) {
                        int prev = newMap[bindIndex[hb]];
                        bindStorage[prev] = -1; bindIndex[prev] = -1;
                    }
                    newMap[bindIndex[hb]] = hb;
                }
            }
        }
        bpSlotBoundToHotbar = newMap;
        dirty = true;
    }

    /** 기존 코드가 호출하는 이름 그대로 유지 */
    public void remapBackpackBindingsAfterResize() { /* resizeBackpack에서 이미 처리 */ }

    /** 배낭 내부 슬롯이 가리키는 바인딩 전부 해제 */
    public void clearBindingsInsideBackpack() {
        for (int i = 0; i < bpSlotBoundToHotbar.length; i++) {
            int hb = bpSlotBoundToHotbar[i];
            if (hb != -1) { bindStorage[hb] = -1; bindIndex[hb] = -1; }
            bpSlotBoundToHotbar[i] = -1;
        }
        dirty = true;
    }

    // ---------- 바인딩 (슬롯 위임) ----------
    public record UtilBinding(Storage storage, int index) {}

    public UtilBinding peekBinding(int hb) {
        if (hb < 0 || hb >= HOTBAR_COUNT) return null;
        if (bindStorage[hb] < 0) return null;
        return new UtilBinding(bindStorage[hb] == 0 ? Storage.BASE : Storage.BACKPACK, bindIndex[hb]);
    }

    public void clearAllUtilityBindings() {
        for (int i = 0; i < HOTBAR_COUNT; i++) { bindStorage[i] = -1; bindIndex[i] = -1; }
        for (int i = 0; i < baseSlotBoundToHotbar.length; i++) baseSlotBoundToHotbar[i] = -1;
        for (int i = 0; i < bpSlotBoundToHotbar.length; i++)  bpSlotBoundToHotbar[i]  = -1;
        dirty = true;
    }
    // 오타 호환
    public void clearAllUtilBindings() { clearAllUtilityBindings(); }

    public void bindFromBase(ServerPlayer sp, int hb, int baseSlot) { rebind(sp, Storage.BASE, hb, baseSlot); }
    public void bindFromBackpack(ServerPlayer sp, int hb, int bpSlot){ rebind(sp, Storage.BACKPACK, hb, bpSlot); }

    public void unbindHotbar(ServerPlayer sp, int hb) {
        if (hb < 0 || hb >= HOTBAR_COUNT) return;
        if (bindStorage[hb] < 0) return;
        Storage s = bindStorage[hb] == 0 ? Storage.BASE : Storage.BACKPACK;
        int idx = bindIndex[hb];

        if (s == Storage.BASE) {
            if (idx >= 0 && idx < baseSlotBoundToHotbar.length && baseSlotBoundToHotbar[idx] == hb)
                baseSlotBoundToHotbar[idx] = -1;
        } else {
            if (idx >= 0 && idx < bpSlotBoundToHotbar.length && bpSlotBoundToHotbar[idx] == hb)
                bpSlotBoundToHotbar[idx] = -1;
        }
        bindStorage[hb] = -1; bindIndex[hb] = -1;
        syncUtilityHotbar(sp);
    }

    public void unbindSlot(ServerPlayer sp, Storage s, int slot) {
        int hb = (s == Storage.BASE)
                ? (slot >= 0 && slot < baseSlotBoundToHotbar.length ? baseSlotBoundToHotbar[slot] : -1)
                : (slot >= 0 && slot < bpSlotBoundToHotbar.length   ? bpSlotBoundToHotbar[slot]   : -1);
        if (hb != -1) unbindHotbar(sp, hb);
    }

    private void rebind(ServerPlayer sp, Storage storage, int hb, int slot) {
        if (hb < 0 || hb >= HOTBAR_COUNT) return;

        // 1) 대상 핫바의 이전 매핑 해제
        if (bindStorage[hb] >= 0) unbindHotbar(sp, hb);
        // 2) 슬롯이 다른 핫바에 묶여 있으면 해제(1:1 보장)
        unbindSlot(sp, storage, slot);

        // 3) 매핑
        bindStorage[hb] = (byte)(storage == Storage.BASE ? 0 : 1);
        bindIndex[hb] = slot;
        if (storage == Storage.BASE) baseSlotBoundToHotbar[slot] = hb;
        else {
            if (slot < 0 || slot >= bpSlotBoundToHotbar.length) return;
            bpSlotBoundToHotbar[slot] = hb;
        }
        // 핫바 실제 아이템은 비워둔다(복사 제거)
        sp.getInventory().setItem(hb, ItemStack.EMPTY);

        syncUtilityHotbar(sp);
    }

    // 서버에서 슬롯 내용이 바뀔 때(소비/드롭 등) 호출
    public void onSlotStackChanged(ServerPlayer sp, Storage s, int slot, ItemStack after) {
        if (after.isEmpty()) unbindSlot(sp, s, slot);
    }

    // ---------- 클라 동기화 헬퍼 ----------
    public void syncUtilityHotbar(ServerPlayer sp) {
        updateBoundHotbar(sp);
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
        dirty = true;
    }

    public void updateBoundHotbar(ServerPlayer sp) { sp.containerMenu.broadcastChanges(); }

    /** 클라에서 숫자 오버레이만 맞추기 위한 바인딩 세터(패킷 수신용) */
    public void clientSetBinding(int hb, Storage storage, int index) {
        if (hb < 0 || hb >= HOTBAR_COUNT) return;
        if (storage == null) { bindStorage[hb] = -1; bindIndex[hb] = -1; return; }
        bindStorage[hb] = (byte)(storage == Storage.BASE ? 0 : 1);
        bindIndex[hb] = index;
    }

    // ---------- NBT 직렬화(기존 코드 호환) ----------
    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Equip", equipment.serializeNBT());
        tag.put("Base2x2", base2x2.serializeNBT());
        CompoundTag bp = new CompoundTag();
        bp.putInt("W", backpackW); bp.putInt("H", backpackH);
        if (backpack != null) bp.put("Items", backpack.serializeNBT());
        bp.put("Vis", backpackItem.save(new CompoundTag()));
        tag.put("Backpack", bp);

        tag.putIntArray("BindIdx", bindIndex);
        byte[] st = new byte[bindStorage.length];
        System.arraycopy(bindStorage, 0, st, 0, st.length);
        tag.putByteArray("BindSt", st);
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        equipment.deserializeNBT(tag.getCompound("Equip"));
        base2x2.deserializeNBT(tag.getCompound("Base2x2"));
        CompoundTag bp = tag.getCompound("Backpack");
        backpackW = bp.getInt("W"); backpackH = bp.getInt("H");
        backpackItem = ItemStack.of(bp.getCompound("Vis"));
        resizeBackpack(backpackW, backpackH);
        if (backpack != null && bp.contains("Items")) backpack.deserializeNBT(bp.getCompound("Items"));

        int[] idx = tag.getIntArray("BindIdx");
        byte[] st = tag.getByteArray("BindSt");
        for (int i = 0; i < HOTBAR_COUNT; i++) {
            bindIndex[i]   = (i < idx.length) ? idx[i] : -1;
            bindStorage[i] = (i < st.length)  ? st[i]  : -1;
        }
        dirty = false;
    }

    // ---------- 기타 호환 메서드 ----------
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void applyWeaponsToHotbar(ServerPlayer sp) { updateBoundHotbar(sp); }

    /** 일부 코드에서 호출하던 링크 보장 함수 – 여기선 noop */
    public static void ensureLink(ItemStack stack) {}
}
