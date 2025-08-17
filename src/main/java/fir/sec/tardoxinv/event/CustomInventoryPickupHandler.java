package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomInventoryPickupHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent e) {
        if (e.isCanceled()) return;

        Player player = e.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;

        ItemEntity itemEnt = e.getItem();
        ItemStack stack    = itemEnt.getItem();
        if (stack.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            // ── 0) slot_type 기반 ‘장비칸’ 자동 장착(무기류 포함) ─────────────────────
            if (stack.hasTag()) {
                String st = stack.getTag().getString("slot_type");
                int target = -1;

                if ("headset".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_HEADSET).isEmpty())
                        target = PlayerEquipment.SLOT_HEADSET;
                } else if ("helmet".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_HELMET).isEmpty())
                        target = PlayerEquipment.SLOT_HELMET;
                } else if ("vest".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_VEST).isEmpty())
                        target = PlayerEquipment.SLOT_VEST;
                } else if ("primary_weapon".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_PRIM1).isEmpty())
                        target = PlayerEquipment.SLOT_PRIM1;
                    else if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_PRIM2).isEmpty())
                        target = PlayerEquipment.SLOT_PRIM2;
                } else if ("secondary_weapon".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_SEC).isEmpty())
                        target = PlayerEquipment.SLOT_SEC;
                } else if ("melee_weapon".equals(st)) {
                    if (eq.getEquipment().getStackInSlot(PlayerEquipment.SLOT_MELEE).isEmpty())
                        target = PlayerEquipment.SLOT_MELEE;
                }

                if (target >= 0 && eq.getEquipment().isItemValid(target, stack)) {
                    // 1개만 장착
                    ItemStack one = stack.copy(); one.setCount(1);
                    eq.getEquipment().setStackInSlot(target, one);

                    // 🔄 클라에 ‘장비칸’까지 동기화 (아래 ③ 패치 필요)
                    SyncEquipmentPacketHandler.syncToClient(sp, eq);

                    // 월드 스택 감소/정리
                    ItemStack remain = stack.copy();
                    remain.shrink(1);
                    if (remain.isEmpty()) itemEnt.discard();
                    else itemEnt.setItem(remain);

                    e.setCanceled(true);
                    sp.take(itemEnt, 1);
                    return; // 장착 성공 시 종료
                }
            }

            // ── 1) 2x2 자동 수납 → 2) 배낭 자동 수납 ───────────────────────────────
            ItemStack current = itemEnt.getItem();
            ItemStack remain  = current.copy();

            GridItemHandler2D base = eq.getBase2x2();
            if (base != null) remain = base.insertAnywhere(remain, false);

            GridItemHandler2D bag = eq.getBackpack2D();
            if (!remain.isEmpty() && bag != null) remain = bag.insertAnywhere(remain, false);

            // 실제로 뭔가 들어갔을 때만 취소
            if (remain.getCount() != current.getCount()) {
                int picked = current.getCount() - (remain.isEmpty() ? 0 : remain.getCount());
                if (remain.isEmpty()) { itemEnt.discard(); sp.take(itemEnt, picked); }
                else { itemEnt.setItem(remain); if (picked > 0) sp.take(itemEnt, picked); }

                e.setCanceled(true);
                SyncEquipmentPacketHandler.syncToClient(sp, eq); // 배낭/2x2 동기화
            }
            // 아무것도 못 넣었으면 cancel 안 함 → 바닐라 처리로 남음
        });
    }
}
