package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.GameRuleRegister;
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

        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;

        // ✅ 커스텀 인벤 OFF면 바닐라에 맡김
        boolean useCustom = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!useCustom) return;

        // ✅ 여기부터는 바닐라 금지
        e.setCanceled(true);

        ItemEntity ie = e.getItem();
        final ItemStack[] worldStack = {ie.getItem()};
        if (worldStack[0].isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            int picked = 0;
            boolean changed = false;

            // ── 0) slot_type 기반 장비칸 자동 장착(비어있을 때 1개만) ─────────────
            if (worldStack[0].hasTag()) {
                String st = worldStack[0].getTag().getString("slot_type");
                int target = -1;

                if      ("headset".equals(st))          { if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_HEADSET).isEmpty()) target = PlayerEquipment.SLOT_HEADSET; }
                else if ("helmet".equals(st))           { if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_HELMET).isEmpty())  target = PlayerEquipment.SLOT_HELMET;  }
                else if ("vest".equals(st))             { if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_VEST).isEmpty())    target = PlayerEquipment.SLOT_VEST;    }
                else if ("primary_weapon".equals(st))   {
                    if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_PRIM1).isEmpty()) target = PlayerEquipment.SLOT_PRIM1;
                    else if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_PRIM2).isEmpty()) target = PlayerEquipment.SLOT_PRIM2;
                }
                else if ("secondary_weapon".equals(st)) { if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_SEC).isEmpty())     target = PlayerEquipment.SLOT_SEC;     }
                else if ("melee_weapon".equals(st))     { if (cap.getEquipment().getStackInSlot(PlayerEquipment.SLOT_MELEE).isEmpty())   target = PlayerEquipment.SLOT_MELEE;   }

                if (target >= 0 && cap.getEquipment().isItemValid(target, worldStack[0])) {
                    ItemStack one = worldStack[0].copy(); one.setCount(1);
                    cap.getEquipment().setStackInSlot(target, one);
                    picked += 1; changed = true;

                    ItemStack remain = worldStack[0].copy();
                    remain.shrink(1);
                    worldStack[0] = remain;
                }
            }

            // ── 1) 2x2 → 2) 배낭 자동 수납 ────────────────────────────────────────
            if (!worldStack[0].isEmpty()) {
                GridItemHandler2D base = cap.getBase2x2();
                if (base != null) {
                    ItemStack after = base.insertAnywhere(worldStack[0], false);
                    if (after.getCount() != worldStack[0].getCount()) { picked += (worldStack[0].getCount() - after.getCount()); changed = true; }
                    worldStack[0] = after;
                }
            }
            if (!worldStack[0].isEmpty()) {
                GridItemHandler2D bag = cap.getBackpack2D();
                if (bag != null) {
                    ItemStack after = bag.insertAnywhere(worldStack[0], false);
                    if (after.getCount() != worldStack[0].getCount()) { picked += (worldStack[0].getCount() - after.getCount()); changed = true; }
                    worldStack[0] = after;
                }
            }

            // ── 2) 결과 반영(바닐라 차단 유지) ───────────────────────────────────
            if (picked > 0) {
                if (worldStack[0].isEmpty()) ie.discard();
                else ie.setItem(worldStack[0]);
                sp.take(ie, picked);
            }
            if (changed) SyncEquipmentPacketHandler.syncToClient(sp, cap);
            // 아무데도 못 넣었으면 바닥에 그대로 남김 (이벤트는 취소 상태라 바닐라 미개입)
        });
    }
}
