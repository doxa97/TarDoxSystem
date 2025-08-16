package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;

public class EquipmentMenu extends AbstractContainerMenu {

    private final Player player;
    private final boolean serverSide;

    private ItemStackHandler equip;
    private ItemStackHandler base2x2;
    private GridItemHandler2D backpack;
    private final int bpW, bpH;

    private static final int SLOT_SIZE = 18;

    private int backpackFirstMenuIndex = -1;
    private int backpackMenuSlots = 0;

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
                base2x2  = cap.getBase2x2();
                backpack = cap.getBackpack2D();
                addSlot(new SlotItemHandler(new BackpackEquipHandler(cap, sp), 0, 90, 40));
            });
        } else {
            equip    = new ItemStackHandler(PlayerEquipment.EQUIP_SLOTS);
            base2x2  = new ItemStackHandler(4);
            backpack = new GridItemHandler2D(Math.max(0, backpackWidth), Math.max(0, backpackHeight));
            addSlot(new SlotItemHandler(new ItemStackHandler(1), 0, 90, 40));
        }

        // 장비
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_HEADSET, 10, 10));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_HELMET,  60, 10));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_VEST,    10, 46));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_PRIM1, 10,  80));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_PRIM2, 10, 106));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_SEC,   10, 132));
        addSlot(new SlotItemHandler(equip, PlayerEquipment.SLOT_MELEE, 10, 158));

        // 기본 2x2
        int baseInvX = 80, baseInvY = 150;
        for (int row = 0; row < 2; row++)
            for (int col = 0; col < 2; col++)
                addSlot(new SlotItemHandler(base2x2, col + row * 2, baseInvX + col * SLOT_SIZE, baseInvY + row * SLOT_SIZE));

        // 배낭 그리드
        int bpX = 148, bpY = 40;
        if (bpW > 0 && bpH > 0) {
            for (int i = 0; i < bpW * bpH; i++) {
                int x = i % bpW;
                int y = i / bpW;
                addSlot(new SlotItemHandler(backpack, i, bpX + x * SLOT_SIZE, bpY + y * SLOT_SIZE));
                if (backpackFirstMenuIndex < 0) backpackFirstMenuIndex = this.slots.size() - 1;
                backpackMenuSlots++;
            }
        }
        // 플레이어 인벤/핫바 슬롯은 붙이지 않음
    }

    private boolean isBackpackMenuSlot(int menuIndex) {
        return backpackFirstMenuIndex >= 0
                && menuIndex >= backpackFirstMenuIndex
                && menuIndex < backpackFirstMenuIndex + backpackMenuSlots;
    }
    private int toBackpackIndex(int menuIndex) { return menuIndex - backpackFirstMenuIndex; }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player p) {
        if (serverSide && isBackpackMenuSlot(slotId) && clickType == ClickType.PICKUP) {
            int bpIndex = toBackpackIndex(slotId);
            GridItemHandler2D gh = backpack;

            ItemStack carried = getCarried();
            if (carried.isEmpty()) {
                // 앵커/클러스터 집기
                ItemStack taken = gh.extractCluster(bpIndex);
                if (!taken.isEmpty()) {
                    setCarried(taken);
                    broadcastChanges();
                }
                return;
            } else {
                // 스왑: 기존꺼 빼고 → 새로 놓기 → 실패 시 원복
                ItemStack prev = gh.extractCluster(bpIndex);
                boolean placed = gh.tryPlaceAt(bpIndex, carried);
                if (placed) {
                    setCarried(prev);
                    broadcastChanges();
                } else {
                    // 원복
                    if (!prev.isEmpty()) gh.tryPlaceAt(bpIndex, prev);
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, p);
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

    @Override
    public void removed(Player p) {
        // 장비창 닫을 때 커서 아이템 커스텀 인벤에 정리(바닐라 핫바로 튀는 것 방지)
        if (serverSide) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                boolean placed = false;
                // 다칸 → 배낭 우선
                int w = carried.hasTag() ? Math.max(1, carried.getTag().getInt("Width")) : 1;
                int h = carried.hasTag() ? Math.max(1, carried.getTag().getInt("Height")) : 1;

                if (w > 1 || h > 1) {
                    placed = backpack.tryPlaceFirstFit(carried);
                } else {
                    // 1x1: 기본 2x2 먼저
                    for (int i = 0; i < base2x2.getSlots(); i++) {
                        if (base2x2.getStackInSlot(i).isEmpty()) {
                            base2x2.setStackInSlot(i, carried.copy());
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) placed = backpack.tryPlaceFirstFit(carried);
                }

                if (!placed) {
                    // 플레이어 일반 인벤 시도 → 실패 시 드롭
                    if (!p.getInventory().add(carried.copy())) {
                        p.drop(carried.copy(), false);
                    }
                }
                setCarried(ItemStack.EMPTY);
                broadcastChanges();
            }
        }
        super.removed(p);
    }

    @Override public ItemStack quickMoveStack(Player p, int i){ return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player p){ return true; }
}
