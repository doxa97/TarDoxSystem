package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean useCustom = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        SyncEquipmentPacketHandler.syncGamerule(sp, useCustom);
        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> SyncEquipmentPacketHandler.syncToClient(sp, cap));
    }

    @SubscribeEvent
    public static void onRightClickOffhand(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (use && e.getHand() == InteractionHand.OFF_HAND) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (use && e.getHand() == InteractionHand.OFF_HAND) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer sp)) return;
        boolean useCustom = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!useCustom) return;

        // 오프핸드 항상 비우기
        if (!sp.getOffhandItem().isEmpty()) {
            if (sp.getMainHandItem().isEmpty()) sp.setItemInHand(InteractionHand.MAIN_HAND, sp.getOffhandItem().copy());
            else sp.getInventory().add(sp.getOffhandItem().copy());
            sp.getOffhandItem().shrink(1);
        }

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            // 유틸 바인딩 동기화(핫바 소비/드롭 → 원본 소모)
            cap.syncUtilityHotbar(sp);

            // 장비 미러(1~4) 필요시
            if (cap.isDirty()) {
                cap.applyWeaponsToHotbar(sp);
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
                cap.clearDirty();
            }
        });
    }

    /** Q 드롭: 1~4는 장비칸 해제, 5~9는 바인딩 원본에서 수량을 차감하여 드롭과 일치시키기 */
    @SubscribeEvent
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent e) {
        if (!(e.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp)) return;

        sp.getCapability(fir.sec.tardoxinv.capability.ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            int hb = sp.getInventory().selected;
            var b = cap.peekBinding(hb);
            if (b == null) return;

            e.setCanceled(true); // 바닐라 드롭 취소

            net.minecraft.world.item.ItemStack src;
            if (b.storage() == PlayerEquipment.Storage.BASE) {
                src = cap.getBase2x2().getStackInSlot(b.index());
            } else {
                if (cap.getBackpack() == null) return;
                src = cap.getBackpack().getStackInSlot(b.index());
            }
            if (src.isEmpty()) { cap.unbindHotbar(sp, hb); return; }

            var drop = src.split(1);
            var ent = sp.drop(drop, false);
            if (ent != null) ent.setThrower(sp.getUUID());

            cap.onSlotStackChanged(sp, b.storage(), b.index(), src);
            cap.syncUtilityHotbar(sp);
        });
    }

}
