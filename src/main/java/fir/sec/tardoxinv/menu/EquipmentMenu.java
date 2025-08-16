package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EquipmentMenu extends AbstractContainerMenu {

    private final Player player;

    // ModMenus의 메뉴 타입을 리플렉션으로 안전하게 받아오되,
    // 실패 시 MenuType<?> 와일드카드로 GENERIC_9x3를 사용해 컴파일/런타임 안전 확보
    private static MenuType<?> resolveType() {
        try {
            Class<?> cls = Class.forName("fir.sec.tardoxinv.menu.ModMenus");
            try {
                var f = cls.getField("EQUIPMENT");
                var reg = f.get(null);
                var get = reg.getClass().getMethod("get");
                return (MenuType<?>) get.invoke(reg);
            } catch (NoSuchFieldException ignore) {
                var f2 = cls.getField("EQUIPMENT_MENU");
                var reg2 = f2.get(null);
                var get2 = reg2.getClass().getMethod("get");
                return (MenuType<?>) get2.invoke(reg2);
            }
        } catch (Throwable t) {
            return MenuType.GENERIC_9x3; // fallback
        }
    }

    /** 기존 호출용 (id, inv) */
    public EquipmentMenu(int id, Inventory inv) {
        super(resolveType(), id);
        this.player = inv.player;
        buildSlots();
    }

    /** 일부 경로에서 (id, inv, w, h) 시그니처로 부르는 경우 호환용 */
    public EquipmentMenu(int id, Inventory inv, int w, int h) {
        this(id, inv);
    }

    private void buildSlots() {
        this.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            // 배낭 장착/해제 슬롯
            addSlot(new BackpackSlot(cap, 80, 18, this));

            // 장비 슬롯들
            var equip = cap.getEquipment();
            if (equip == null) return;

            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_HEADSET, 10, 10));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_HELMET,  60, 10));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_VEST,    10, 46));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_PRIM1,   10,  80));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_PRIM2,   10, 106));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_SEC,     10, 132));
            addSlot(new net.minecraftforge.items.SlotItemHandler(equip, PlayerEquipment.SLOT_MELEE,   10, 158));
        });
    }

    @Override
    public boolean stillValid(Player p) { return true; }

    /** shift-클릭 이동 비활성화(바닐라 이동 방지) */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 메뉴 종료 시 커서 아이템이 바닐라처럼 핫바로 튀는 걸 방지:
     * 1) 커스텀 인벤토리로 삽입 시도
     * 2) 실패 시 드롭
     */
    @Override
    public void removed(Player p) {
        super.removed(p);
        if (p.level().isClientSide) return;

        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) return;

        p.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            ItemStack remain = tryInsertIntoCustomInventory(cap, carried);
            if (remain.isEmpty()) {
                this.setCarried(ItemStack.EMPTY);
            } else if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.drop(remain, false);
                this.setCarried(ItemStack.EMPTY);
            }
        });
    }

    private ItemStack tryInsertIntoCustomInventory(PlayerEquipment cap, ItemStack stack) {
        ItemStack work = stack.copy();

        var base = (fir.sec.tardoxinv.capability.GridItemHandler2D) cap.getBase2x2();
        work = base.insertItem2D(work, false);
        if (work.isEmpty()) return ItemStack.EMPTY;

        if (!cap.getBackpackItem().isEmpty() && cap.getBackpackWidth() > 0 && cap.getBackpackHeight() > 0) {
            var bp = (fir.sec.tardoxinv.capability.GridItemHandler2D) cap.getBackpack();
            work = bp.insertItem2D(work, false);
        }
        return work;
    }

    /** 배낭 1칸짜리 컨테이너 슬롯 */
    private static final class BackpackSlot extends Slot {
        private final PlayerEquipment cap;
        private final EquipmentMenu menu;

        public BackpackSlot(PlayerEquipment cap, int x, int y, EquipmentMenu menu) {
            super(new BackpackContainer(cap), 0, x, y);
            this.cap = cap;
            this.menu = menu;
        }

        @Override public boolean mayPlace(ItemStack stack) {
            var t = stack.getTag();
            return t != null && (t.contains("Width") || t.contains("Height"));
        }

        @Override public boolean mayPickup(Player player) {
            return !cap.getBackpackItem().isEmpty();
        }

        @Override public ItemStack getItem() { return cap.getBackpackItem(); }

        @Override public void set(ItemStack stack) {
            cap.setBackpackItem(stack);
            int w = readSize(stack, "Width");
            int h = readSize(stack, "Height");
            cap.resizeBackpack(Math.max(0, w), Math.max(0, h));
            cap.remapBackpackBindingsAfterResize();
            if (menu.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            }
        }

        @Override public ItemStack remove(int amount) {
            ItemStack cur = cap.getBackpackItem();
            if (cur.isEmpty()) return ItemStack.EMPTY;
            cap.setBackpackItem(ItemStack.EMPTY);
            cap.resizeBackpack(0, 0);
            cap.remapBackpackBindingsAfterResize();
            if (menu.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            }
            return cur;
        }

        private int readSize(ItemStack st, String key) {
            var t = st.getTag();
            return (t != null && t.contains(key)) ? Math.max(0, t.getInt(key)) : 0;
        }
    }

    /** 배낭 1칸짜리 컨테이너 래퍼 */
    private static final class BackpackContainer implements Container {
        private final PlayerEquipment cap;
        BackpackContainer(PlayerEquipment cap) { this.cap = cap; }
        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { return cap.getBackpackItem().isEmpty(); }
        @Override public ItemStack getItem(int slot) { return cap.getBackpackItem(); }
        @Override public ItemStack removeItem(int slot, int amount) {
            ItemStack cur = cap.getBackpackItem();
            cap.setBackpackItem(ItemStack.EMPTY);
            cap.resizeBackpack(0,0);
            cap.remapBackpackBindingsAfterResize();
            return cur;
        }
        @Override public ItemStack removeItemNoUpdate(int slot) {
            ItemStack cur = cap.getBackpackItem();
            cap.setBackpackItem(ItemStack.EMPTY);
            cap.resizeBackpack(0,0);
            cap.remapBackpackBindingsAfterResize();
            return cur;
        }
        @Override public void setItem(int slot, ItemStack stack) { cap.setBackpackItem(stack); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { cap.setBackpackItem(ItemStack.EMPTY); cap.resizeBackpack(0,0); }
    }
}
