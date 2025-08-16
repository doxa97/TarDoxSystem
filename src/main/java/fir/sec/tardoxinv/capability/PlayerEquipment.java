package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.*;

public class PlayerEquipment {

    public static final int SLOT_HEADSET = 0;
    public static final int SLOT_HELMET  = 1;
    public static final int SLOT_VEST    = 2;
    public static final int SLOT_PRIM1   = 3;
    public static final int SLOT_PRIM2   = 4;
    public static final int SLOT_SEC     = 5;
    public static final int SLOT_MELEE   = 6;
    public static final int EQUIP_SLOTS  = 7;

    /** 유틸 바인딩에서 어떤 저장소/인덱스를 가리키는지 표기 */
    public enum Storage { BASE, BACKPACK }

    /** 유틸 바인딩 정보(핫바 ↔ 저장소 위치) */
    public static class UtilBinding {
        public final Storage storage;
        public final int index; // BASE: 0..3, BACKPACK: 0..(w*h-1)
        public UtilBinding(Storage storage, int index) {
            this.storage = storage;
            this.index = index;
        }
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
            UUID la = LinkIdUtil.getLinkId(a), lb = LinkIdUtil.getLinkId(b);
            if (la != null && la.equals(lb)) setStackInSlot(changedSlot, ItemStack.EMPTY);
        }
    };

    private final ItemStackHandler base2x2 = new ItemStackHandler(4) {
        @Override protected void onContentsChanged(int slot) { dirty = true; }
    };

    /** 배낭: 2D 그리드 */
    private GridItemHandler2D backpack = new GridItemHandler2D(0, 0);
    private int backpackWidth  = 0;
    private int backpackHeight = 0;
    private ItemStack backpackItem = ItemStack.EMPTY;

    private boolean dirty = false;

    public ItemStackHandler getEquipment() { return equipment; }
    public ItemStackHandler getBase2x2() { return base2x2; }
    /** 호환용: 기존 코드가 getBackpack()을 사용하므로 유지 */
    public ItemStackHandler getBackpack() { return backpack; }
    public GridItemHandler2D getBackpack2D() { return backpack; }

    public int getBackpackWidth() { return backpackWidth; }
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
        backpack = new GridItemHandler2D(w, h) {
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
        // 유틸 바인딩 저장
        CompoundTag binds = new CompoundTag();
        for (int hb = 4; hb <= 8; hb++) {
            UtilBinding b = utilBindings.get(hb);
            if (b != null) {
                CompoundTag t = new CompoundTag();
                t.putString("storage", b.storage == Storage.BASE ? "base" : "backpack");
                t.putInt("index", b.index);
                binds.put(String.valueOf(hb), t);
            }
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

        utilBindings.clear();
        if (tag.contains("UtilBinds")) {
            CompoundTag binds = tag.getCompound("UtilBinds");
            for (int hb = 4; hb <= 8; hb++) {
                String k = String.valueOf(hb);
                if (binds.contains(k)) {
                    CompoundTag t = binds.getCompound(k);
                    String s = t.getString("storage");
                    int idx = t.getInt("index");
                    Storage st = "base".equals(s) ? Storage.BASE : Storage.BACKPACK;
                    utilBindings.put(hb, new UtilBinding(st, idx));
                }
            }
        }

        for (int i = 0; i < EQUIP_SLOTS; i++) {
            ItemStack s = equipment.getStackInSlot(i);
            if (!s.isEmpty()) LinkIdUtil.ensureLinkId(s);
        }
        dirty = false;
    }

    public static void ensureLink(ItemStack stack) { if (!stack.isEmpty()) LinkIdUtil.ensureLinkId(stack); }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    // ─────────────────────────────────────
    // 유틸 바인딩 (핫바 5~9 → 4..8 인덱스)
    // ─────────────────────────────────────
    private final Map<Integer, UtilBinding> utilBindings = new HashMap<>();
    private final Map<UUID, Integer> utilHotbarCount = new HashMap<>();
    private final Map<Integer, UUID> utilHotbarSlotId = new HashMap<>();

    /** 서버: BASE에서 바인딩 */
    public void bindFromBase(ServerPlayer sp, int hotbarIdx, int baseIndex) {
        utilBindings.put(hotbarIdx, new UtilBinding(Storage.BASE, baseIndex));
        utilHotbarSlotId.remove(hotbarIdx); // 수량 연동은 별개(원한다면 유지 가능)
        dirty = true;
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    /** 서버: BACKPACK에서 바인딩 */
    public void bindFromBackpack(ServerPlayer sp, int hotbarIdx, int bpIndex) {
        utilBindings.put(hotbarIdx, new UtilBinding(Storage.BACKPACK, bpIndex));
        utilHotbarSlotId.remove(hotbarIdx);
        dirty = true;
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    /** 서버: 전체 바인딩 초기화(배낭 드롭 시) */
    public void clearAllUtilBindings() {
        utilBindings.clear();
        utilHotbarSlotId.clear();
        utilHotbarCount.clear();
        dirty = true;
    }
    /** 기존 코드 호환용 이름 */
    public void clearAllUtilityBindings() { clearAllUtilBindings(); }

    /** 클라이언트: Overlay/툴팁 동기화용 조회 */
    public UtilBinding peekBinding(int hotbarIdx) {
        return utilBindings.get(hotbarIdx);
    }

    /** 클라이언트: 서버 동기화 적용 */
    public void clientSetBinding(int hotbarIdx, Storage storage, int index) {
        if (storage == null) {
            utilBindings.remove(hotbarIdx);
        } else {
            utilBindings.put(hotbarIdx, new UtilBinding(storage, index));
        }
    }

    /** 기존 수량동기 기록(유지, 필요시 사용) */
    public void recordUtilityAssignment(ServerPlayer sp, int hotbarIdx, UUID id) {
        utilHotbarSlotId.entrySet().removeIf(e -> e.getValue().equals(id));
        utilHotbarSlotId.put(hotbarIdx, id);
        ItemStack hb = sp.getInventory().getItem(hotbarIdx);
        utilHotbarCount.put(id, hb.getCount());
        dirty = true;
    }
}
