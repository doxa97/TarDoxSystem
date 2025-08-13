package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Objects;
import java.util.UUID;

public class PlayerEquipment {

    public static final int SLOT_HEADSET = 0;
    public static final int SLOT_HELMET  = 1;
    public static final int SLOT_VEST    = 2;
    public static final int SLOT_PRIM1   = 3;
    public static final int SLOT_PRIM2   = 4;
    public static final int SLOT_SEC     = 5;
    public static final int SLOT_MELEE   = 6;
    public static final int EQUIP_SLOTS  = 7;

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
            UUID la = LinkIdUtil.getLinkId(a), lb = LinkIdUtil.getLinkId(b);
            if (la != null && la.equals(lb)) setStackInSlot(changedSlot, ItemStack.EMPTY);
        }
    };

    private final ItemStackHandler base2x2 = new ItemStackHandler(4) {
        @Override protected void onContentsChanged(int slot) { dirty = true; }
    };

    private ItemStackHandler backpack = new ItemStackHandler(0) {
        @Override protected void onContentsChanged(int slot) { dirty = true; }
    };
    private int backpackWidth  = 0;
    private int backpackHeight = 0;
    private ItemStack backpackItem = ItemStack.EMPTY;
    private boolean dirty = false;

    public ItemStackHandler getEquipment() { return equipment; }
    public ItemStackHandler getBase2x2()  { return base2x2; }
    public ItemStackHandler getBackpack() { return backpack; }
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

    public void resizeBackpack(int w, int h) {
        backpackWidth = w;
        backpackHeight = h;
        int size = Math.max(0, w * h);
        backpack = new ItemStackHandler(size) {
            @Override protected void onContentsChanged(int slot) { dirty = true; }
        };
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
    }

    public static void ensureLink(ItemStack stack) { if (!stack.isEmpty()) LinkIdUtil.ensureLinkId(stack); }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    // --- 유틸 핫바 동기화 상태 ---
    private final java.util.Map<java.util.UUID, Integer> utilHotbarCount = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.UUID> utilHotbarSlotId = new java.util.HashMap<>();

    /** 유틸 핫바에 등록/재등록될 때 서버에서 즉시 매핑 갱신 */
    public void recordUtilityAssignment(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx, java.util.UUID id) {
        // 같은 id가 다른 번호에 있으면 제거
        utilHotbarSlotId.entrySet().removeIf(e -> e.getValue().equals(id));
        utilHotbarSlotId.put(hotbarIdx, id);

        ItemStack hb = sp.getInventory().getItem(hotbarIdx);
        utilHotbarCount.put(id, hb.getCount());

        dirty = true;
    }

    /** 틱마다: 핫바 수량 감소 → 저장소에서 소모 / 0이면 핫바도 비움 */
    public void syncUtilityHotbar(net.minecraft.server.level.ServerPlayer sp) {
        for (int i = 4; i <= 8; i++) {
            ItemStack st = sp.getInventory().getItem(i);
            java.util.UUID id = LinkIdUtil.getLinkId(st);

            if (id == null) {
                utilHotbarSlotId.remove(i);
                continue;
            }
            utilHotbarSlotId.put(i, id);

            int now = st.getCount();
            int prev = utilHotbarCount.getOrDefault(id, now);

            if (now < prev) {
                int delta = prev - now;
                consumeFromStorageByLink(id, delta);
            }

            // ★ 핫바가 0개가 되었고, 저장소에도 더 이상 없으면 핫바 슬롯 비우기
            if (now <= 0 && countInStorageByLink(id) <= 0) {
                sp.getInventory().setItem(i, ItemStack.EMPTY);
                utilHotbarSlotId.remove(i);
                utilHotbarCount.remove(id);
                sp.inventoryMenu.broadcastChanges();
            } else {
                utilHotbarCount.put(id, Math.max(now, 0));
            }
        }
    }

    private void consumeFromStorageByLink(java.util.UUID id, int delta) {
        if (delta <= 0) return;
        for (int s = 0; s < base2x2.getSlots() && delta > 0; s++) {
            ItemStack it = base2x2.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                int take = Math.min(delta, it.getCount());
                it.shrink(take);
                if (it.isEmpty()) base2x2.setStackInSlot(s, ItemStack.EMPTY);
                delta -= take;
            }
        }
        for (int s = 0; s < backpack.getSlots() && delta > 0; s++) {
            ItemStack it = backpack.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                int take = Math.min(delta, it.getCount());
                it.shrink(take);
                if (it.isEmpty()) backpack.setStackInSlot(s, ItemStack.EMPTY);
                delta -= take;
            }
        }
        dirty = true;
    }

    private int countInStorageByLink(java.util.UUID id) {
        int total = 0;
        for (int s = 0; s < base2x2.getSlots(); s++) {
            ItemStack it = base2x2.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                total += it.getCount();
            }
        }
        for (int s = 0; s < backpack.getSlots(); s++) {
            ItemStack it = backpack.getStackInSlot(s);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) {
                total += it.getCount();
            }
        }
        return total;
    }
}
