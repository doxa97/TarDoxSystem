package fir.sec.tardoxinv.capability;

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
            var la = LinkIdUtil.getLinkId(a);
            var lb = LinkIdUtil.getLinkId(b);
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

        // 바인딩 저장(지속성)
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

        // 바인딩 복원
        utilBindByHotbar.clear();
        lastCountByHotbar.clear();
        if (tag.contains("UtilBinds", Tag.TAG_LIST)) {
            ListTag binds = tag.getList("UtilBinds", Tag.TAG_COMPOUND);
            for (Tag t : binds) {
                CompoundTag b = (CompoundTag) t;
                int hb = b.getInt("Hotbar");
                String st = b.getString("Storage");
                int idx = b.getInt("Index");
                UUID id = b.contains("Id") ? b.getUUID("Id") : null;
                Storage storage = "base".equals(st) ? Storage.BASE : Storage.BACKPACK;
                utilBindByHotbar.put(hb, new UtilBinding(storage, idx, id));
            }
        }
    }

    public static void ensureLink(ItemStack stack) { if (!stack.isEmpty()) LinkIdUtil.ensureLinkId(stack); }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }

    // ---------- 유틸 바인딩(핫바 5~9) ----------
    public enum Storage { BASE, BACKPACK }
    public static class UtilBinding {
        public final Storage storage;
        public final int index;
        public final UUID id;
        public UtilBinding(Storage s, int idx, UUID id) { this.storage = s; this.index = idx; this.id = id; }
    }

    private final Map<Integer, UtilBinding> utilBindByHotbar = new HashMap<>();   // key: 4..8
    private final Map<Integer, Integer>     lastCountByHotbar = new HashMap<>();  // key: 4..8 → 이전 틱 핫바 수량

    /** 클라이언트(오버레이) 전용: 서버가 보낸 바인딩 스냅샷을 수신해서 로컬 표시용으로 세팅 */
    public void clientSetBinding(int hotbarIdx, Storage storage, int index) {
        if (storage == null) {
            utilBindByHotbar.remove(hotbarIdx);
        } else {
            utilBindByHotbar.put(hotbarIdx, new UtilBinding(storage, index, null));
        }
    }
    /** 클라이언트 오버레이가 읽는 getter */
    public UtilBinding peekBinding(int hotbarIdx) { return utilBindByHotbar.get(hotbarIdx); }

    /** ★ 같은 id/같은 원본을 가리키던 다른 슬롯과, 대상 슬롯의 기존 바인딩을 모두 해제 후 새로 바인딩 */
    private void bindInternal(net.minecraft.server.level.ServerPlayer sp, int hotbarIdx, UtilBinding b) {
        // 1) 같은 id 또는 같은 원본(storage+index)을 가진 다른 핫바 슬롯 수집
        List<Integer> toClear = new ArrayList<>();
        for (Map.Entry<Integer, UtilBinding> e : utilBindByHotbar.entrySet()) {
            int otherIdx = e.getKey();
            if (otherIdx == hotbarIdx) continue;
            UtilBinding ob = e.getValue();
            boolean sameId = (b.id != null && ob.id != null && b.id.equals(ob.id));
            boolean sameSource = (ob.storage == b.storage && ob.index == b.index);
            if (sameId || sameSource) toClear.add(otherIdx);
        }
        // 2) 실제 해제
        for (int idx : toClear) unbind(sp, idx);
        // 3) 대상 슬롯 기존 바인딩 해제
        if (utilBindByHotbar.containsKey(hotbarIdx)) unbind(sp, hotbarIdx);

        // 4) 새 바인딩 적용
        utilBindByHotbar.put(hotbarIdx, b);
        lastCountByHotbar.put(hotbarIdx, 0); // 초기 소진 오인 방지
        mirrorOne(sp, hotbarIdx);

        // 5) 클라 오버레이 동기화
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
        // 오버레이 동기화
        SyncEquipmentPacketHandler.syncUtilBindings(sp, this);
    }

    public boolean isHotbarBound(int hotbarIdx) { return utilBindByHotbar.containsKey(hotbarIdx); }
    public boolean isUtilityIdAssigned(UUID id)  { return utilBindByHotbar.values().stream().anyMatch(b -> Objects.equals(b.id, id)); }

    /** 매 틱: 원본→핫바 미러링 & 핫바에서 소비된 만큼 원본 감소 */
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

        // 원본 무효/변경 → 해제
        if (src.isEmpty() || !src.hasTag() || !"utility".equals(src.getTag().getString("slot_type"))) { unbind(sp, i); return; }
        UUID srcId = LinkIdUtil.getLinkId(src);
        if (b.id != null && (srcId == null || !b.id.equals(srcId))) { unbind(sp, i); return; }

        ItemStack hb  = sp.getInventory().getItem(i);
        int hbCount   = hb.isEmpty() ? 0 : hb.getCount();
        int prev      = lastCountByHotbar.getOrDefault(i, hbCount);

        if (hbCount < prev) {
            int used = prev - hbCount;
            if (used > 0) {
                ItemStack s = src.copy();
                s.shrink(used);
                setSource(b, s.isEmpty() ? ItemStack.EMPTY : s);
                src = s.isEmpty() ? ItemStack.EMPTY : s;
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
}
