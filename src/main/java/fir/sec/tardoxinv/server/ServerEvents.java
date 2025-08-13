package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber
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

        // 유틸 바인딩 미러링
        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            cap.tickMirrorUtilityHotbar(sp);
            if (cap.isDirty()) {
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
                cap.clearDirty();
            }
        });
    }

    /** 유틸 사용 직후 즉시 미러링(딜레이 없이) */
    @SubscribeEvent
    public static void onUtilityFinishUse(LivingEntityUseItemEvent.Finish e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!use) return;
        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> cap.tickMirrorUtilityHotbar(sp));
    }

    /** 드롭 차단 제거: 장비칸 정리만 유지(무기 등) */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!use) return;

        ItemStack tossed = e.getEntity().getItem();
        var id = fir.sec.tardoxinv.util.LinkIdUtil.getLinkId(tossed);
        if (id == null) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            var eq = cap.getEquipment();
            for (int slot : new int[]{
                    PlayerEquipment.SLOT_PRIM1,
                    PlayerEquipment.SLOT_PRIM2,
                    PlayerEquipment.SLOT_SEC,
                    PlayerEquipment.SLOT_MELEE
            }) {
                ItemStack s = eq.getStackInSlot(slot);
                if (!s.isEmpty() && s.hasTag() && s.getTag().hasUUID("link_id")
                        && id.equals(s.getTag().getUUID("link_id"))) {
                    eq.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            cap.applyWeaponsToHotbar(sp);
            SyncEquipmentPacketHandler.syncToClient(sp, cap);
        });
    }
}
