package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 습득 우선순위:
 *   1) 기본 2x2
 *   2) 배낭
 *   3) 둘 다 실패 → 바닐라(땅에 남음)
 *
 * 서버에서만 실제 삽입/동기화 수행.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomInventoryPickupHandler {

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        ItemEntity ie = e.getItem();
        ItemStack   drop = ie.getItem();
        if (drop.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            // 🔹 배낭 아이템인가?
            boolean isBackpack = drop.hasTag() && "backpack".equals(drop.getTag().getString("slot_type"));
            if (isBackpack) {
                // 이미 배낭 장착 중이면: 절대 주우지 않음(월드 유지)
                if (eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0) {
                    ie.setPickUpDelay(40);
                    e.setCanceled(true);
                    return;
                }
                // 미장착이면: 자동 장착 (바닐라 유입 차단)
                eq.setBackpackItem(drop.copy());
                ie.discard();
                e.setCanceled(true);
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                return;
            }

            // 🔹 일반 아이템 라우팅 (기존 우선순위 유지)
            // 1) 기본 2x2
            GridItemHandler2D base = eq.getBase2x2();
            int id = findFirstFit(base, drop);
            if (id >= 0) {
                base.insertItem(id, drop.copy(), false);
                ie.discard();
                e.setCanceled(true);
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                return;
            }

            // 2) 배낭
            if (eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0) {
                GridItemHandler2D bp = eq.getBackpack2D();
                int id2 = findFirstFit(bp, drop);
                if (id2 >= 0) {
                    bp.insertItem(id2, drop.copy(), false);
                    ie.discard();
                    e.setCanceled(true);
                    SyncEquipmentPacketHandler.syncToClient(sp, eq);
                    return;
                }
            }

            // 3) 못 넣으면 바닐라에 맡김(캔슬 안 함)
        });
    }


    private static int findFirstFit(GridItemHandler2D inv, ItemStack st) {
        if (inv == null || st.isEmpty()) return -1;
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.canPlaceAt(i, st)) return i;
        }
        return -1;
    }
}
