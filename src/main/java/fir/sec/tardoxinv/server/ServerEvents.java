package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

        // 유틸 핫바 ↔ 저장소 동기화
        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            cap.syncUtilityHotbar(sp);
            if (cap.isDirty()) {
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
                cap.clearDirty();
            }
        });
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!use) return;

        ItemStack tossed = e.getEntity().getItem();              // 던져질 스택
        int selected = sp.getInventory().selected;
        ItemStack sel = sp.getInventory().getItem(selected);

        // 1) 선택 슬롯과 동일(타입+태그)이면 핫바 드랍으로 간주
        boolean fromSelected = !sel.isEmpty() && ItemStack.isSameItemSameTags(sel, tossed);

        // 2) 핫바 0~8 중 어디에서든 동일(타입+태그)이면 핫바 드랍으로 간주
        boolean fromAnyHotbar = false;
        if (!fromSelected) {
            for (int i = 0; i < 9; i++) {
                ItemStack hb = sp.getInventory().getItem(i);
                if (!hb.isEmpty() && ItemStack.isSameItemSameTags(hb, tossed)) {
                    fromAnyHotbar = true;
                    break;
                }
            }
        }

        if (fromSelected || fromAnyHotbar) {
            // 핫바 드랍 차단 + 수량 복구
            e.setCanceled(true);

            // 클라가 이미 줄였을 수 있으니 복구 보정
            // (선택 슬롯 우선 복구, 아니면 동일한 핫바 슬롯 찾아 복구)
            boolean restored = false;
            if (!sel.isEmpty() && ItemStack.isSameItemSameTags(sel, tossed)) {
                ItemStack fix = sel.copy();
                fix.grow(tossed.getCount());
                sp.getInventory().setItem(selected, fix);
                restored = true;
            }
            if (!restored) {
                for (int i = 0; i < 9; i++) {
                    ItemStack hb = sp.getInventory().getItem(i);
                    if (!hb.isEmpty() && ItemStack.isSameItemSameTags(hb, tossed)) {
                        ItemStack fix = hb.copy();
                        fix.grow(tossed.getCount());
                        sp.getInventory().setItem(i, fix);
                        break;
                    }
                }
            }

            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
            return;
        }

        // ▼ 인벤 화면 등에서 드랍 → 정상 드랍 허용
        java.util.UUID id = LinkIdUtil.getLinkId(tossed);
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
