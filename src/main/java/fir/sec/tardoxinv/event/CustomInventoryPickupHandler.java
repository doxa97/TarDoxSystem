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
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraftforge.eventbus.api.EventPriority;

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent e) {
        // 이미 누군가 취소했으면 무시
        if (e.isCanceled()) return;

        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;

        ItemEntity ie = e.getItem();
        ItemStack drop = ie.getItem();
        if (drop.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            // ---------- 0) 장비 태그 기반 자동 장착 ----------
            ItemStack work = drop.copy();
            if (work.hasTag()) {
                String st = work.getTag().getString("slot_type");
                int target = -1;
                var equip = eq.getEquipment(); // ItemStackHandler(장비칸)

                if ("headset".equals(st)) {
                    target = PlayerEquipment.SLOT_HEADSET;
                } else if ("helmet".equals(st)) {
                    target = PlayerEquipment.SLOT_HELMET;
                } else if ("vest".equals(st)) {
                    target = PlayerEquipment.SLOT_VEST;
                } else if ("primary_weapon".equals(st)) {
                    // 주무기 1 → 2 우선
                    if (equip.getStackInSlot(PlayerEquipment.SLOT_PRIM1).isEmpty()) {
                        target = PlayerEquipment.SLOT_PRIM1;
                    } else if (equip.getStackInSlot(PlayerEquipment.SLOT_PRIM2).isEmpty()) {
                        target = PlayerEquipment.SLOT_PRIM2;
                    }
                } else if ("secondary_weapon".equals(st)) {
                    target = PlayerEquipment.SLOT_SEC;
                } else if ("melee_weapon".equals(st)) {
                    target = PlayerEquipment.SLOT_MELEE;
                }

                if (target >= 0
                        && equip.getStackInSlot(target).isEmpty()
                        && equip.isItemValid(target, work)) {

                    // 한 개만 장착
                    ItemStack one = work.copy();
                    one.setCount(1);
                    equip.setStackInSlot(target, one);
                    SyncEquipmentPacketHandler.syncToClient(sp, eq);

                    // 장착 후 남은 수량(있다면)을 계속 처리
                    work.shrink(1);
                    if (work.isEmpty()) {
                        ie.discard();          // 전량 소모
                        e.setCanceled(true);
                        sp.take(ie, 1);        // 줍는 애니/사운드
                        return;
                    } else {
                        // 월드 스택을 남은 수량으로 갱신
                        ie.setItem(work.copy());
                        e.setCanceled(true);   // 기본 바닐라 픽업 막음
                        sp.take(ie, 1);
                    }
                }
            }

            // ---------- 1) 기본 2x2 ----------
            GridItemHandler2D base = eq.getBase2x2();
            ItemStack current = ie.getItem();
            int idx = findFirstFit(base, current);
            if (idx >= 0) {
                base.insertItem(idx, current.copy(), false);
                ie.discard();
                e.setCanceled(true);
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                return;
            }

            // ---------- 2) 배낭 ----------
            if (eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0) {
                GridItemHandler2D bp = eq.getBackpack2D();
                int id2 = findFirstFit(bp, current);
                if (id2 >= 0) {
                    bp.insertItem(id2, current.copy(), false);
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
