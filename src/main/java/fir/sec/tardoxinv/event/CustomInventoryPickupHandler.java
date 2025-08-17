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
        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;

        ItemEntity ie = e.getItem();
        ItemStack drop = ie.getItem();
        if (drop.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            // 1) 기본 2x2 먼저
            GridItemHandler2D base = eq.getBase2x2();
            int idx = findFirstFit(base, drop);
            if (idx >= 0) {
                base.insertItem2D(idx, drop.copy(), false);
                ie.discard();
                e.setCanceled(true);
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                return;
            }

            // 2) 배낭로
            if (eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0) {
                GridItemHandler2D bp = eq.getBackpack2D(); // ← new 브랜치 메서드명
                int id2 = findFirstFit(bp, drop);
                if (id2 >= 0) {
                    bp.insertItem2D(id2, drop.copy(), false);
                    ie.discard();
                    e.setCanceled(true);
                    SyncEquipmentPacketHandler.syncToClient(sp, eq);
                }
            }
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
