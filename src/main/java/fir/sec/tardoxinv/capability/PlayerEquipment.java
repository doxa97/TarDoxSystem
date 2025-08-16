package fir.sec.tardoxinv.capability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import fir.sec.tardoxinv.menu.grid.GridItemHandler2D;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;

/**
 * 바인딩을 “복사”가 아닌 “슬롯 교체(위임)”로 보장.
 * - 한 핫바 인덱스에는 최대 1개 슬롯만 연결
 * - 한 슬롯은 최대 1개 핫바에만 연결(역바인딩 존재)
 * - 드롭/소진 시 자동 해제
 */
public class PlayerEquipment {

    public enum Storage { BASE, BACKPACK }

    public static final int HOTBAR_COUNT = 9;

    // --- 기존 보유 필드(가정) ---
    private final GridItemHandler2D base2x2_2D = new GridItemHandler2D(2, 2);
    private GridItemHandler2D backpack2D = null; // 배낭 장착 시 생성/할당
    // 무기 장비칸 등 기존 필드/메서드는 그대로 유지

    // --- 유틸 바인딩 상태(양방향 맵) ---
    /** 핫바 -> (저장소, 인덱스). 없으면 storage=-1, index=-1 */
    private final byte[] bindStorage = new byte[HOTBAR_COUNT];
    private final int[]  bindIndex   = new int[HOTBAR_COUNT];

    /** BASE 슬롯 -> 바인딩된 핫바(없으면 -1) */
    private final int[]  baseSlotBoundToHotbar;
    /** BACKPACK 슬롯 -> 바인딩된 핫바(없으면 -1). 배낭 크기는 가변이므로 동적 보정 */
    private int[]        bpSlotBoundToHotbar = new int[0];

    public PlayerEquipment() {
        for (int i = 0; i < HOTBAR_COUNT; i++) { bindStorage[i] = -1; bindIndex[i] = -1; }
        baseSlotBoundToHotbar = new int[base2x2_2D.getSlots()];
        for (int i = 0; i < baseSlotBoundToHotbar.length; i++) baseSlotBoundToHotbar[i] = -1;
    }

    // ----- 공개 getter (기존 코드 호환) -----
    public GridItemHandler2D getBase2x2_2D() { return base2x2_2D; }
    public GridItemHandler2D getBackpack2D() { return backpack2D; }

    /** 클라 HUD 등에서 읽어가는 프리뷰용 */
    public UtilBinding peekBinding(int hb) {
        if (hb < 0 || hb >= HOTBAR_COUNT) return null;
        if (bindStorage[hb] < 0) return null;
        return new UtilBinding(bindStorage[hb] == 0 ? Storage.BASE : Storage.BACKPACK, bindIndex[hb]);
    }

    // 배낭 장착/탈착 시 호출(기존 장착 로직에서 연결)
    public void setBackpack(GridItemHandler2D newBp, ServerPlayer sp) {
        this.backpack2D = newBp;
        // 배낭 슬롯 수가 바뀌면 역바인딩 테이블 재구성 및 범위 벗어난 바인딩 해제
        int newSize = (backpack2D == null) ? 0 : backpack2D.getSlots();
        int[] newMap = new int[newSize];
        for (int i = 0; i < newMap.length; i++) newMap[i] = -1;

        // 기존 매핑 재검증
        for (int hb = 0; hb < HOTBAR_COUNT; hb++) {
            if (bindStorage[hb] == 1) { // BACKPACK
                if (bindIndex[hb] < 0 || bindIndex[hb] >= newSize) {
                    // 존재하지 않는 슬롯을 가리키면 해제
                    bindStorage[hb] = -1; bindIndex[hb] = -1;
                } else {
                    if (newMap[bindIndex[hb]] != -1) {
                        // 동일 슬롯을 두 핫바가 가리키면 먼저 것 해제
                        int prevHb = newMap[bindIndex[hb]];
                        bindStorage[prevHb] = -1; bindIndex[prevHb] = -1;
                    }
                    newMap[bindIndex[hb]] = hb;
                }
            }
        }
        this.bpSlotBoundToHotbar = newMap;
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    // ===== 바인딩 API =====

    public record UtilBinding(Storage storage, int index) {}

    public void clearAllUtilityBindings() {
        for (int i = 0; i < HOTBAR_COUNT; i++) { bindStorage[i] = -1; bindIndex[i] = -1; }
        for (int i = 0; i < baseSlotBoundToHotbar.length; i++) baseSlotBoundToHotbar[i] = -1;
        for (int i = 0; i < bpSlotBoundToHotbar.length; i++)    bpSlotBoundToHotbar[i] = -1;
    }
    // 과거 코드 호환용(오타 방지)
    public void clearAllUtilBindings() { clearAllUtilityBindings(); }

    public void bindFromBase(ServerPlayer sp, int hotbarIndex, int baseSlot) {
        rebind(sp, Storage.BASE, hotbarIndex, baseSlot);
    }
    public void bindFromBackpack(ServerPlayer sp, int hotbarIndex, int bpSlot) {
        rebind(sp, Storage.BACKPACK, hotbarIndex, bpSlot);
    }

    public void unbindHotbar(ServerPlayer sp, int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_COUNT) return;
        if (bindStorage[hotbarIndex] < 0) return;
        Storage s = bindStorage[hotbarIndex] == 0 ? Storage.BASE : Storage.BACKPACK;
        int idx   = bindIndex[hotbarIndex];

        if (s == Storage.BASE) {
            if (idx >= 0 && idx < baseSlotBoundToHotbar.length && baseSlotBoundToHotbar[idx] == hotbarIndex)
                baseSlotBoundToHotbar[idx] = -1;
        } else {
            if (idx >= 0 && idx < bpSlotBoundToHotbar.length && bpSlotBoundToHotbar[idx] == hotbarIndex)
                bpSlotBoundToHotbar[idx] = -1;
        }
        bindStorage[hotbarIndex] = -1;
        bindIndex[hotbarIndex]   = -1;

        updateBoundHotbar(sp);
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    public void unbindSlot(ServerPlayer sp, Storage storage, int slotIndex) {
        int hb = (storage == Storage.BASE)
                ? (slotIndex >= 0 && slotIndex < baseSlotBoundToHotbar.length ? baseSlotBoundToHotbar[slotIndex] : -1)
                : (slotIndex >= 0 && slotIndex < bpSlotBoundToHotbar.length   ? bpSlotBoundToHotbar[slotIndex]   : -1);
        if (hb != -1) unbindHotbar(sp, hb);
    }

    private void rebind(ServerPlayer sp, Storage storage, int hotbarIndex, int slotIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= HOTBAR_COUNT) return;
        // 1) 대상 핫바가 이전에 가리키던 슬롯 정리
        if (bindStorage[hotbarIndex] >= 0) {
            unbindHotbar(sp, hotbarIndex);
        }
        // 2) 동일 슬롯을 가리키는 다른 핫바가 있으면 해제(슬롯은 1:1)
        unbindSlot(sp, storage, slotIndex);

        // 3) 새로운 매핑 설정
        bindStorage[hotbarIndex] = (byte)(storage == Storage.BASE ? 0 : 1);
        bindIndex[hotbarIndex]   = slotIndex;
        if (storage == Storage.BASE) {
            ensureInRange(slotIndex, baseSlotBoundToHotbar.length);
            baseSlotBoundToHotbar[slotIndex] = hotbarIndex;
        } else {
            ensureInRange(slotIndex, bpSlotBoundToHotbar.length);
            bpSlotBoundToHotbar[slotIndex] = hotbarIndex;
        }

        // 4) 핫바 실제 아이템은 비워둔다(복사본 제거). 조작은 이벤트로 위임.
        sp.getInventory().setItem(hotbarIndex, ItemStack.EMPTY);

        updateBoundHotbar(sp);
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    private static void ensureInRange(int idx, int size) {
        if (idx < 0 || idx >= size) throw new IndexOutOfBoundsException("slot=" + idx + ", size=" + size);
    }

    /**
     * 클라 핫바 표시 갱신(빈 슬롯 유지). 필요시 슬롯 텍스트/오버레이 동기화 등 추가 훅 존재 가능.
     */
    public void updateBoundHotbar(ServerPlayer sp) {
        sp.containerMenu.broadcastChanges();
    }

    // ----- 드롭/소진 시 슬롯이 비면 자동 해제에 쓰이는 헬퍼 -----
    public void onSlotStackChanged(ServerPlayer sp, Storage storage, int slotIndex, ItemStack after) {
        if (after.isEmpty()) unbindSlot(sp, storage, slotIndex);
    }
}
