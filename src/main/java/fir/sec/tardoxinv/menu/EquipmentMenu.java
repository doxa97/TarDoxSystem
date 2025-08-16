package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.inventory.GridItemHandler2D;
import fir.sec.tardoxinv.menu.slot.GridSlot;
import fir.sec.tardoxinv.server.BackpackReopenHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class EquipmentMenu extends AbstractContainerMenu {

    public static final int TEX_W = 256, TEX_H = 256;
    public static final int BASE_X = 80,  BASE_Y = 150; // 기본 2x2 시작 좌표(텍스처 원점)
    public static final int PACK_X = 148, PACK_Y = 36;  // 배낭 그리드 시작 좌표(텍스처 원점)

    private final Inventory playerInv;
    private final PlayerEquipment equipment;

    public EquipmentMenu(int id, Inventory inv, PlayerEquipment eq, MenuType<?> type) {
        super(type, id);
        this.playerInv = inv;
        this.equipment = eq;

        // 기본 2x2
        final GridItemHandler2D base = eq.getBase2x2();
        for (int by = 0; by < 2; by++) {
            for (int bx = 0; bx < 2; bx++) {
                final int idx = by * 2 + bx;
                this.addSlot(new GridSlot(GridSlot.Storage.BASE, base, idx,
                        BASE_X + bx * 18, BASE_Y + by * 18));
            }
        }

        // 배낭 그리드
        if (eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0 && eq.getBackpack() != null) {
            final GridItemHandler2D backpack = eq.getBackpack();
            final int bw = eq.getBackpackWidth(), bh = eq.getBackpackHeight();
            for (int by = 0; by < bh; by++) {
                for (int bx = 0; bx < bw; bx++) {
                    final int idx = by * bw + bx;
                    this.addSlot(new GridSlot(GridSlot.Storage.BACKPACK, backpack, idx,
                            PACK_X + bx * 18, PACK_Y + by * 18));
                }
            }
        }

        // (플레이어 인벤/핫바 슬롯은 네 프로젝트에서 기존대로 addSlot)
    }

    /** 배낭 아이템이 장착/해제되어 w,h가 바뀌었을 때 호출 (메뉴 내 호출 지점 제공용) */
    public void onBackpackChangedServer(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        BackpackReopenHelper.onBackpackChanged(sp, this.equipment);
    }

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            final ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                // 커스텀 인벤 삽입 시도 → 실패 시 드롭
                ItemStack rem = carried;
                if (equipment.getBase2x2() != null) {
                    rem = equipment.getBase2x2().insertItem2D(rem, false);
                }
                if (!rem.isEmpty() && equipment.getBackpack() != null) {
                    rem = equipment.getBackpack().insertItem2D(rem, false);
                }
                if (!rem.isEmpty()) {
                    player.drop(rem.copy(), false);
                }
                this.setCarried(ItemStack.EMPTY);
            }
        }
    }
}
