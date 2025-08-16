package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * PlayerEquipment
 * - 장비슬롯(7), 기본 2x2, 배낭 2D
 * - 유틸 바인딩(핫바→원본 슬롯 링크), 스냅샷 동기화
 * - 기존 호출부(saveNBT/loadNBT/toTag/getBackpack2D/getEquipment/...) 모두 제공
 */
public class PlayerEquipment {

    public static final int EQUIP_SLOTS = 7;
    public static final int SLOT_PRIM1 = 0, SLOT_PRIM2 = 1, SLOT_SEC = 2, SLOT_MELEE = 3;
    public static final int SLOT_HELMET = 4, SLOT_VEST = 5, SLOT_HEADSET = 6;

    public enum Storage { BASE, BACKPACK }
    public static record BindingRef(Storage storage, int index) {}

    private final ItemStackHandler equipment = new ItemStackHandler(EQUIP_SLOTS);
    private final GridItemHandler2D base2x2 = new GridItemHandler2D(2, 2);

    private ItemStack backpackItem = ItemStack.EMPTY;
    private GridItemHandler2D backpack2D = null;
    private int backpackW = 0, backpackH = 0;

    private boolean dirty = false;

    private final Map<Integer, BindingRef> utilBinds = new HashMap<>();
    private static final int MAX_HB = 64;

    public ItemStackHandler getEquipment() { return equipment; }
    public GridItemHandler2D getBase2x2() { return base2x2; }
    public GridItemHandler2D getBackpack() { return backpack2D; }
    public GridItemHandler2D getBackpack2D() { return backpack2D; }
    public ItemStack getBackpackItem() { return backpackItem; }
    public int getBackpackWidth() { return backpackW; }
    public int getBackpackHeight() { return backpackH; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    public void applyWeaponsToHotbar(ServerPlayer sp) { /* no-op */ }

    public void setBackpackItem(ItemStack stack) {
        this.backpackItem = stack.copy();
        if (this.backpackItem.isEmpty()) {
            this.backpackW = 0;
            this.backpackH = 0;
            this.backpack2D = null;
            clearBindingsInsideBackpack();
        } else {
            CompoundTag tag = this.backpackItem.getTag();
            int w = (tag != null && tag.contains("Width")) ? tag.getInt("Width") : 0;
            int h = (tag != null && tag.contains("Height")) ? tag.getInt("Height") : 0;
            resizeBackpack(w, h);
        }
        this.dirty = true;
    }

    public void resizeBackpack(int w, int h) {
        this.backpackW = Math.max(0, w);
        this.backpackH = Math.max(0, h);
        this.backpack2D = (backpackW > 0 && backpackH > 0) ? new GridItemHandler2D(backpackW, backpackH) : null;
        remapBackpackBindingsAfterResize();
        this.dirty = true;
    }

    public void remapBackpackBindingsAfterResize() {
        if (backpack2D == null) {
            clearBindingsInsideBackpack();
        } else {
            utilBinds.entrySet().removeIf(e ->
                    e.getValue().storage() == Storage.BACKPACK &&
                            (e.getValue().index() < 0 || e.getValue().index() >= backpackW * backpackH)
            );
        }
    }

    public void bindFromBase(ServerPlayer sp, int hotbar, int baseIndex) {
        removeDuplicates(Storage.BASE, baseIndex);
        utilBinds.put(hotbar, new BindingRef(Storage.BASE, baseIndex));
        syncUtilityHotbar(sp);
    }

    public void bindFromBackpack(ServerPlayer sp, int hotbar, int packIndex) {
        removeDuplicates(Storage.BACKPACK, packIndex);
        utilBinds.put(hotbar, new BindingRef(Storage.BACKPACK, packIndex));
        syncUtilityHotbar(sp);
    }

    public BindingRef peekBinding(int hotbar) { return utilBinds.get(hotbar); }

    public void unbindHotbar(ServerPlayer sp, int hotbar) {
        utilBinds.remove(hotbar);
        syncUtilityHotbar(sp);
    }

    public void onSlotStackChanged(ServerPlayer sp, Storage storage, int index, ItemStack newStack) {
        syncUtilityHotbar(sp);
    }

    public void clearBindingsInsideBackpack() {
        utilBinds.entrySet().removeIf(e -> e.getValue().storage() == Storage.BACKPACK);
    }

    public void clearAllUtilityBindings() { utilBinds.clear(); }

    private void removeDuplicates(Storage storage, int index) {
        utilBinds.entrySet().removeIf(e -> e.getValue().storage() == storage && e.getValue().index() == index);
    }

    public BindSnapshot getBindingSnapshot() {
        byte[] st = new byte[MAX_HB];
        int[] idx  = new int[MAX_HB];
        for (int i = 0; i < MAX_HB; i++) { st[i] = 0; idx[i] = -1; }
        utilBinds.forEach((hb, br) -> {
            if (hb < 0 || hb >= MAX_HB) return;
            st[hb] = (byte)(br.storage() == Storage.BASE ? 1 : 2);
            idx[hb] = br.index();
        });
        return new BindSnapshot(st, idx);
    }

    public static final class BindSnapshot {
        public final byte[] storageByHb;
        public final int[]  indexByHb;
        public BindSnapshot(byte[] s, int[] i) { this.storageByHb = s; this.indexByHb = i; }
    }

    public void syncUtilityHotbar(ServerPlayer sp) {
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Equip", equipment.serializeNBT());
        tag.put("Base", base2x2.serializeNBT());
        if (!backpackItem.isEmpty()) tag.put("BackpackItem", backpackItem.save(new CompoundTag()));
        tag.putInt("BW", backpackW);
        tag.putInt("BH", backpackH);
        if (backpack2D != null) tag.put("Backpack2D", backpack2D.serializeNBT());

        BindSnapshot snap = getBindingSnapshot();
        tag.putByteArray("BindST", snap.storageByHb);
        int[] idx = snap.indexByHb;
        ListTag il = new ListTag();
        for (int v : idx) {
            CompoundTag it = new CompoundTag();
            it.putInt("v", v);
            il.add(it);
        }
        tag.put("BindIDX", il);
        return tag;
    }

    public void loadNBT(CompoundTag tag) {
        if (tag.contains("Equip")) equipment.deserializeNBT(tag.getCompound("Equip"));
        if (tag.contains("Base"))  base2x2.deserializeNBT(tag.getCompound("Base"));

        this.backpackItem = tag.contains("BackpackItem") ? ItemStack.of(tag.getCompound("BackpackItem")) : ItemStack.EMPTY;
        this.backpackW = tag.getInt("BW");
        this.backpackH = tag.getInt("BH");
        this.backpack2D = (backpackW > 0 && backpackH > 0) ? new GridItemHandler2D(backpackW, backpackH) : null;
        if (backpack2D != null && tag.contains("Backpack2D")) backpack2D.deserializeNBT(tag.getCompound("Backpack2D"));

        utilBinds.clear();
        byte[] st = tag.getByteArray("BindST");
        ListTag il = tag.getList("BindIDX", Tag.TAG_COMPOUND);
        int n = Math.min(st.length, il.size());
        for (int hb = 0; hb < n; hb++) {
            int idx = il.getCompound(hb).getInt("v");
            if (st[hb] == 0 || idx < 0) continue;
            Storage s = (st[hb] == 1) ? Storage.BASE : Storage.BACKPACK;
            utilBinds.put(hb, new BindingRef(s, idx));
        }
    }

    public CompoundTag toTag() { return saveNBT(); }
}
