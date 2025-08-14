package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;

public class EquipmentMenu extends AbstractContainerMenu {

    private final Player player;
    private final boolean serverSide;

    private ItemStackHandler equip;
    private GridItemHandler2D base2x2;
    private GridItemHandler2D backpack;
    private final int bpW, bpH;

    private static final int SLOT_SIZE = 18;

    public static class BackpackEquipHandler extends ItemStackHandler {
        private final PlayerEquipment cap;
        private final ServerPlayer sp;
        public BackpackEquipHandler(PlayerEquipment cap, ServerPlayer sp) { super(1); this.cap = cap; this.sp = sp; }

        @Override public ItemStack getStackInSlot(int slot){ return cap.getBackpackItem(); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.hasTag() && "backpack".equals(stack.getTag().getString("slot_type"));
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack) || !cap.getBackpackItem().isEmpty()) return stack;
            if (!simulate) {
                cap.setBackpackItem(stack.copy());
                sp.containerMenu.broadcastChanges();
                reopen();
            }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        private void reopen() {
            int w = cap.getBackpackWidth(), h = cap.getBackpackHeight();
            NetworkHooks.openScreen(
                    sp,
                    new SimpleMenuProvider(
                            (id, inv, ply) -> new EquipmentMenu(id, inv, w, h),
                            Component.literal("Equipment")
                    ),
                    buf -> { buf.writeVarInt(w); buf.writeVarInt(h); }
            );
        }
    }

    public EquipmentMenu(int id, Inventory playerInventory, int backpackWidth, int backpackHeight) {
        super(ModMenus.EQUIPMENT_MENU.get(), id);
        this.player = playerInventory.player;
        this.serverSide = !player.level().isClientSide;
        this.bpW = backpackWidth;
        this.bpH = backpackHeight;

        if (serverSide) {
            ServerPlayer sp = (ServerPlayer) player;
            player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                equip    = cap.getEquipment();
                base2x2  = cap.getBase2x2();    // 2x2
                backpack = cap.getBackpack2D(); // 2D
                addSlot(new net.minecraftforge.items.SlotItemHandler(new BackpackEquipHandler(cap, sp), 0, 90, 40));
            });
        } else {
            equip    = new ItemStackHandler(PlayerEquipment.EQUIP_SLOTS);
            base2x2  = new GridItemHandler2D(2, 2);
            backpack = new GridItemHandler2D(Math.max(0, backpackWidth), Math.max(0, backpackHeight));
            addSlot(new net.minecraftforge.items.SlotItemHandler(new ItemStackHandler(1), 0, 90, 40));
        }

        // 장비 슬롯(타입 강제) — 기능 2
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_HEADSET, 10, 10,  "headset"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_HELMET,  60, 10,  "helmet"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_VEST,    10, 46,  "vest"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_PRIM1,   10, 80,  "primary_weapon"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_PRIM2,   10, 106, "primary_weapon"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_SEC,     10, 132, "secondary_weapon"));
        addSlot(new CustomSlot(equip, PlayerEquipment.SLOT_MELEE,   10, 158, "melee_weapon"));

        // 기본 2x2 (GridSlot)
        int baseInvX = 80, baseInvY = 150;
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 2; col++){
                int idx = col + row * 2;
                addSlot(new GridSlot(base2x2, idx, baseInvX + col * SLOT_SIZE, baseInvY + row * SLOT_SIZE, GridSlot.Storage.BASE));
            }

        // 배낭 그리드
        int bpX = 148, bpY = 40;
        if (bpW > 0 && bpH > 0) {
            for (int i = 0; i < bpW * bpH; i++) {
                int x = i % bpW;
                int y = i / bpW;
                addSlot(new GridSlot(backpack, i, bpX + x * SLOT_SIZE, bpY + y * SLOT_SIZE, GridSlot.Storage.BACKPACK));
            }
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!serverSide) return;
        player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            if (cap.isDirty()) {
                cap.applyWeaponsToHotbar(player);
                cap.clearDirty();
            }
        });
    }

    @Override public ItemStack quickMoveStack(Player p, int i){ return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p){ return true; }
}
