package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.item.ModItems;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class PlayerEquipment {

    public static final int SLOT_HEADSET = 0;
    public static final int SLOT_HELMET  = 1;
    public static final int SLOT_VEST    = 2;
    public static final int SLOT_PRIM1   = 3;
    public static final int SLOT_PRIM2   = 4;
    public static final int SLOT_SEC     = 5;
    public static final int SLOT_MELEE   = 6;
    public static final int EQUIP_SLOTS  = 7;

    public static final int BASE_W = 2;   // 기존 값 사용
    public static final int BASE_H = 2;

    private final GridItemHandler2D base2x2;
    private final GridItemHandler2D backpack;

    public PlayerEquipment(/* 기존 파라미터 */) {
        // ... other init ...
        this.base2x2  = new GridItemHandler2D(BASE_W, BASE_H);
        this.backpack = new GridItemHandler2D(0, 0);
    }


    public GridItemHandler2D getBackpack() { return backpack; }

    // 배낭 아이템 판별: 아이템 클래스/태그 기준(당신 프로젝트의 규칙에 맞게)
// 여기선 태그로 폭/높이를 보유한 스택을 배낭으로 취급
    public static boolean isBackpackItem(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CompoundTag t = s.getTag();
        return t != null && (t.contains("BackpackW") || t.contains("BackpackH") || t.contains("BackpackInv"));
    }

    private static int readBackpackW(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return 0;
        if (t.contains("BackpackW")) return Math.max(0, t.getInt("BackpackW"));
        if (t.contains("BPW"))       return Math.max(0, t.getInt("BPW"));
        // 최후 수단: Width 사용 금지(그리드 아이템과 충돌 가능), 필요시 주석 해제
        // if (t.contains("Width")) return Math.max(0, t.getInt("Width"));
        return 0;
    }

    private static int readBackpackH(ItemStack s) {
        CompoundTag t = s.getTag();
        if (t == null) return 0;
        if (t.contains("BackpackH")) return Math.max(0, t.getInt("BackpackH"));
        if (t.contains("BPH"))       return Math.max(0, t.getInt("BPH"));
        // if (t.contains("Height")) return Math.max(0, t.getInt("Height"));
        return 0;
    }

    // 실제로 장착: NBT를 절대 건드리지 않고, 그 크기로 핸들러 리사이즈
    public void equipBackpackFromItem(ItemStack src) {
        if (src == null || src.isEmpty()) return;
        // 기존의 readBPW/BPH / BackpackInv 복원 로직 제거
        setBackpackItem(src); // ← 장착은 이 함수 하나로 통일
    }


    // 해제: 내부 그리드를 0x0으로
    public void unequipBackpack() {
        setBackpackSize(0, 0);
    }

    private final ItemStackHandler equipment = new ItemStackHandler(EQUIP_SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            if (slot == SLOT_PRIM1 || slot == SLOT_PRIM2) normalizePrimaryUnique(slot);
            dirty = true;
        }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty() || !stack.hasTag()) return false;
            String t = stack.getTag().getString("slot_type");
            return switch (slot) {
                case SLOT_HEADSET -> Objects.equals(t, "headset");
                case SLOT_HELMET  -> Objects.equals(t, "helmet");
                case SLOT_VEST    -> Objects.equals(t, "vest");
                case SLOT_PRIM1, SLOT_PRIM2 -> Objects.equals(t, "primary_weapon");
                case SLOT_SEC     -> Objects.equals(t, "secondary_weapon");
                case SLOT_MELEE   -> Objects.equals(t, "melee_weapon");
                default -> false;
            };
        }
        @Override public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty()) LinkIdUtil.ensureLinkId(stack);
            super.setStackInSlot(slot, stack);
            dirty = true;
        }
        private void normalizePrimaryUnique(int changedSlot) {
            ItemStack a = getStackInSlot(SLOT_PRIM1);
            ItemStack b = getStackInSlot(SLOT_PRIM2);
            if (a.isEmpty() || b.isEmpty()) return;
            var la = LinkIdUtil.getLinkId(a);
            var lb = LinkIdUtil.getLinkId(b);
            if (la != null && la.equals(lb)) setStackInSlot(changedSlot, ItemStack.EMPTY);
        }
    };

    // ★ 2D 그리드 인벤토리로 교체
    private int backpackWidth  = 0;
    private int backpackHeight = 0;
    private ItemStack backpackItem = ItemStack.EMPTY;

    private boolean dirty = false;

    public ItemStackHandler getEquipment() { return equipment; }
    public GridItemHandler2D getBase2x2()  { return base2x2; }
    public GridItemHandler2D getBackpack2D() { return backpack; } // 주: 기존 getBackpack()을 사용하는 코드가 있으면 이 메서드로 교체
    public int getBackpackWidth()  { return backpackWidth; }
    public int getBackpackHeight() { return backpackHeight; }
    public ItemStack getBackpackItem() { return backpackItem; }

    public void setBackpackItem(ItemStack newBackpack) {
        if (!backpackItem.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.putInt("Width",  backpackWidth);
            data.putInt("Height", backpackHeight);
            data.put("Items", backpack.serializeNBT());
            backpackItem.getOrCreateTag().put("BackpackData", data);
        }
        backpackItem = newBackpack.copy();
        if (backpackItem.isEmpty()) { resizeBackpack(0, 0); dirty = true; return; }

        CompoundTag tag = backpackItem.getOrCreateTag();
        if (!tag.contains("slot_type")) tag.putString("slot_type", "backpack");
        LinkIdUtil.ensureLinkId(backpackItem);

        int w = Math.max(0, tag.getInt("Width"));
        int h = Math.max(0, tag.getInt("Height"));
        resizeBackpack(w, h);

        if (tag.contains("BackpackData")) {
            CompoundTag data = tag.getCompound("BackpackData");
            backpackWidth  = data.getInt("Width");
            backpackHeight = data.getInt("Height");
            resizeBackpack(backpackWidth, backpackHeight);
            backpack.deserializeNBT(data.getCompound("Items"));
        }
        dirty = true;
    }
    public void setBackpackSize(int w, int h) {
        if (w < 0 || h < 0) { w = 0; h = 0; }
        // 0x0 로 줄이기 전에 남은 아이템 정리(정책: 드롭 or BASE로 이동 중 택1)
        // 최소 패치: 그냥 0x0로 줄이되 내부가 안전하게 비워지도록
        // GridItemHandler2D가 out-of-bounds를 스스로 막도록 구현되어 있음.
        backpack.setGridSize(w, h);
    }
    public void resizeBackpack(int w, int h) {
        backpackWidth = w;
        backpackHeight = h;
        backpack.setGridSize(w,h);
        dirty = true;
    }

    public void applyWeaponsToHotbar(Player player) {
        if (player == null || player.level().isClientSide) return;
        for (int s : new int[]{SLOT_PRIM1, SLOT_PRIM2, SLOT_SEC, SLOT_MELEE}) {
            ItemStack eq = equipment.getStackInSlot(s);
            if (!eq.isEmpty()) ensureLink(eq);
        }
        player.getInventory().setItem(0, equipment.getStackInSlot(SLOT_PRIM1).copy());
        player.getInventory().setItem(1, equipment.getStackInSlot(SLOT_PRIM2).copy());
        player.getInventory().setItem(2, equipment.getStackInSlot(SLOT_SEC).copy());
        player.getInventory().setItem(3, equipment.getStackInSlot(SLOT_MELEE).copy());
        player.inventoryMenu.broadcastChanges();
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Equipment", equipment.serializeNBT());
        tag.put("Base2x2",  base2x2.serializeNBT());
        CompoundTag bp = new CompoundTag();
        bp.putInt("Width",  backpackWidth);
        bp.putInt("Height", backpackHeight);
        bp.put("Items", backpack.serializeNBT());
        tag.put("Backpack", bp);
        if (!backpackItem.isEmpty()) tag.put("BackpackItem", backpackItem.save(new CompoundTag()));

        // 바인딩 저장
        ListTag binds = new ListTag();
        for (Map.Entry<Integer, UtilBinding> e : utilBindByHotbar.entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putInt("Hotbar", e.getKey());
            b.putString("Storage", e.getValue().storage == Storage.BASE ? "base" : "backpack");
            b.putInt("Index", e.getValue().index);
            if (e.getValue().id != null) b.putUUID("Id", e.getValue().id);
            binds.add(b);
        }
        tag.put("UtilBinds", binds);
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        equipment.deserializeNBT(tag.getCompound("Equipment"));
        base2x2.deserializeNBT(tag.getCompound("Base2x2"));

        if (tag.contains("Backpack")) {
            CompoundTag bp = tag.getCompound("Backpack");
            int w = bp.getInt("Width");
            int h = bp.getInt("Height");
            resizeBackpack(w, h);
            backpack.deserializeNBT(bp.getCompound("Items"));
        } else {
            resizeBackpack(0, 0);
        }
        if (tag.contains("BackpackItem")) backpackItem = ItemStack.of(tag.getCompound("BackpackItem"));
        else backpackItem = ItemStack.EMPTY;

        for (int i = 0; i < EQUIP_SLOTS; i++) {
            ItemStack s = equipment.getStackInSlot(i);
            if (!s.isEmpty()) LinkIdUtil.ensureLinkId(s);
        }
        dirty = false;

        utilBindByHotbar.clear();
        lastCountByHotbar.clear();
        if (tag.contains("UtilBinds", Tag.TAG_LIST)) {
            ListTag binds = tag.getList("UtilBinds", Tag.TAG_COMPOUND);
            for (Tag t : binds) {
                CompoundTag b = (CompoundTag) t;
                int hb = b.getInt("Hotbar");
                String st = b.getString("Storage");
                int idx = b.getInt("Index");
                java.util.UUID id = b.contains("Id") ? b.getUUID("Id") : null;
                Storage storage = "base".equals(st) ? Storage.BASE : Storage.BACKPACK;
                utilBindByHotbar.put(hb, new UtilBinding(storage, idx, id));
            }
        }
    }

    public static void ensureLink(ItemStack stack) { if (!stack.isEmpty()) LinkIdUtil.ensureLinkId(stack); }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    // ---------- 유틸 바인딩 ----------
    public enum Storage { BASE, BACKPACK }
    public static class UtilBinding {
        public final Storage storage;
        public final int index;
        public final java.util.UUID id;
        public UtilBinding(Storage s, int idx, java.util.UUID id) { this.storage = s; this.index = idx; this.id = id; }
    }

    private final Map<Integer, UtilBinding> utilBindByHotbar = new HashMap<>();
    private final Map<Integer, Integer>     lastCountByHotbar = new HashMap<>();

    public void clientSetBinding(int hotbarIdx, Storage storage, int index) {
        if (storage == null) utilBindByHotbar.remove(hotbarIdx);
        else utilBindByHotbar.put(hotbarIdx, new UtilBinding(storage, index, null));
    }
    public UtilBinding peekBinding(int hotbarIdx) { return utilBindByHotbar.get(hotbarIdx); }

    private void bindInternal(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx, UtilBinding b) {
        java.util.List<Integer> toClear = new java.util.ArrayList<>();
        for (Map.Entry<Integer, UtilBinding> e : utilBindByHotbar.entrySet()) {
            int otherIdx = e.getKey();
            if (otherIdx == hotbarIdx) continue;
            UtilBinding ob = e.getValue();
            boolean sameId = (b.id != null && ob.id != null && b.id.equals(ob.id));
            boolean sameSource = (ob.storage == b.storage && ob.index == b.index);
            if (sameId || sameSource) toClear.add(otherIdx);
        }
        for (int idx : toClear) unbind(sp, idx);
        if (utilBindByHotbar.containsKey(hotbarIdx)) unbind(sp, hotbarIdx);

        utilBindByHotbar.put(hotbarIdx, b);
        lastCountByHotbar.put(hotbarIdx, 0);
        mirrorOne(sp, hotbarIdx);
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    public void bindFromBase(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx, int baseIndex) {
        ItemStack src = base2x2.getStackInSlot(baseIndex);
        if (src.isEmpty() || !src.hasTag() || !"utility".equals(src.getTag().getString("slot_type"))) return;
        LinkIdUtil.ensureLinkId(src);
        base2x2.setStackInSlot(baseIndex, src);
        bindInternal(sp, hotbarIdx, new UtilBinding(Storage.BASE, baseIndex, LinkIdUtil.getLinkId(src)));
    }

    public void bindFromBackpack(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx, int bpIndex) {
        ItemStack src = backpack.getStackInSlot(bpIndex);
        if (src.isEmpty() || !src.hasTag() || !"utility".equals(src.getTag().getString("slot_type"))) return;
        LinkIdUtil.ensureLinkId(src);
        backpack.setStackInSlot(bpIndex, src);
        bindInternal(sp, hotbarIdx, new UtilBinding(Storage.BACKPACK, bpIndex, LinkIdUtil.getLinkId(src)));
    }

    public void unbind(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx) {
        utilBindByHotbar.remove(hotbarIdx);
        lastCountByHotbar.remove(hotbarIdx);
        sp.getInventory().setItem(hotbarIdx, ItemStack.EMPTY);
        forceHotbarSlot(sp, hotbarIdx);
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    public void clearAllUtilityBindings(){
        utilBindByHotbar.clear();
        lastCountByHotbar.clear();
    }

    public void tickMirrorUtilityHotbar(net.minecraft.server.level.ServerPlayer sp) {
        for (int i = 4; i <= 8; i++) mirrorOne(sp, i);
    }

    private void mirrorOne(net.minecraft.server.level.ServerPlayer sp, int i) {
        UtilBinding b = utilBindByHotbar.get(i);
        if (b == null) return;

        ItemStack src = switch (b.storage) {
            case BASE -> base2x2.getStackInSlot(b.index);
            case BACKPACK -> backpack.getStackInSlot(b.index);
        };

        if (src.isEmpty() || !src.hasTag() || !"utility".equals(src.getTag().getString("slot_type"))) { unbind(sp, i); return; }
        java.util.UUID srcId = LinkIdUtil.getLinkId(src);
        if (b.id != null && (srcId == null || !b.id.equals(srcId))) { unbind(sp, i); return; }

        ItemStack hb  = sp.getInventory().getItem(i);
        int hbCount   = hb.isEmpty() ? 0 : hb.getCount();
        int prev      = lastCountByHotbar.getOrDefault(i, hbCount);

        if (hbCount < prev) {
            int used = prev - hbCount;
            if (used > 0) {
                ItemStack s = src.copy();
                s.shrink(used);
                // 앵커에 반영(0이 되면 영역 해제)
                if (s.isEmpty()) setSource(b, ItemStack.EMPTY);
                else setSource(b, s);
                src = s;
            }
        }

        if (src.isEmpty()) { unbind(sp, i); return; }

        ItemStack mirror = src.copy();
        sp.getInventory().setItem(i, mirror);
        forceHotbarSlot(sp, i);
        lastCountByHotbar.put(i, mirror.getCount());
    }

    private void setSource(UtilBinding b, ItemStack newStack) {
        if (b.storage == Storage.BASE) base2x2.setStackInSlot(b.index, newStack);
        else                           backpack.setStackInSlot(b.index, newStack);
        dirty = true;
    }

    public static void forceHotbarSlot(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx0to8) {
        ItemStack stack = sp.getInventory().getItem(hotbarIdx0to8).copy();
        sp.connection.send(new ClientboundContainerSetSlotPacket(
                sp.inventoryMenu.containerId,
                sp.inventoryMenu.incrementStateId(),
                36 + hotbarIdx0to8,
                stack
        ));
    }

    // --- 유틸 소비 보조: link_id 기준 저장소 소모 및 집계 (앵커만 순회) ---
    public void consumeFromStorageByLink(java.util.UUID id, int delta) {
        if (delta <= 0) return;
        delta = consumeInHandler(base2x2, id, delta);
        delta = consumeInHandler(backpack, id, delta);
        dirty = true;
    }

    private static int consumeInHandler(GridItemHandler2D h, java.util.UUID id, int delta){
        for (int s = 0; s < h.getSlots() && delta > 0; s++) {
            ItemStack it = h.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                int take = Math.min(delta, it.getCount());
                ItemStack after = it.copy();
                after.shrink(take);
                h.setStackInSlot(s, after.isEmpty() ? ItemStack.EMPTY : after);
                delta -= take;
            }
        }
        return delta;
    }

    public int countInStorageByLink(java.util.UUID id) {
        return countInHandler(base2x2, id) + countInHandler(backpack, id);
    }

    private static int countInHandler(GridItemHandler2D h, java.util.UUID id){
        int total = 0;
        for (int s = 0; s < h.getSlots(); s++) {
            ItemStack it = h.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                total += it.getCount();
            }
        }
        return total;
    }
    private ItemStack equippedBackpackItem = ItemStack.EMPTY;



    private static int readBPW(ItemStack s) { CompoundTag t = s.getTag(); return (t!=null && t.contains("BackpackW"))? Math.max(0,t.getInt("BackpackW")):0; }
    private static int readBPH(ItemStack s) { CompoundTag t = s.getTag(); return (t!=null && t.contains("BackpackH"))? Math.max(0,t.getInt("BackpackH")):0; }

    private static void writeBPSize(ItemStack s, int w, int h) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt("BackpackW", Math.max(0,w));
        t.putInt("BackpackH", Math.max(0,h));
    }

    // 배낭 컨텐츠/크기를 아이템 NBT로 저장
    public void packBackpackToItem(ItemStack target) {
        if (target == null || target.isEmpty()) return;
        CompoundTag inv = this.getBackpack().serializeNBT().copy();
        CompoundTag tag = target.getOrCreateTag();
        tag.put("BackpackInv", inv);
        writeBPSize(target, this.getBackpack().getWidth(), this.getBackpack().getHeight());
    }

    // 아이템 NBT에서 배낭 컨텐츠/크기를 불러와 장착


    // 장착 해제(컨텐츠를 아이템에 저장하고 내부 0×0)
    public ItemStack unequipBackpackToItem() {
        if (this.getBackpack().getSlots() > 0) {
            // 보관 중인 아이템 없으면 새 스택 생성(프로젝트의 배낭 Item을 사용)
            ItemStack out = this.equippedBackpackItem.isEmpty() ? this.equippedBackpackItem = this.equippedBackpackItem.copy() : this.equippedBackpackItem.copy();
            if (out.isEmpty()) {
                // TODO: 프로젝트의 배낭 아이템 타입으로 생성 (예: ModItems.BACKPACK.get().getDefaultInstance())
                out = new ItemStack(ModItems.SMALL_BACKPACK.get());
            }
            packBackpackToItem(out);
            this.setBackpackSize(0, 0);
            this.equippedBackpackItem = ItemStack.EMPTY;
            return out;
        }
        return ItemStack.EMPTY;
    }
}
