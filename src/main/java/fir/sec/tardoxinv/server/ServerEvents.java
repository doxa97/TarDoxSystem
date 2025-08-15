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

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            // ★ syncUtilityHotbar 또는 tickMirrorUtilityHotbar 둘 중 있는 걸 호출
            callUtilitySync(cap, sp);

            if (cap.isDirty()) {
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
                cap.clearDirty();
            }
        });
    }

    /** 드롭 차단은 하지 않되, 핫바(1~4)에서 무기 드롭 시 장비칸을 비워서 동기화 유지 */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!use) return;

        ItemStack tossed = e.getEntity().getItem();
        int selected = sp.getInventory().selected; // 드롭 시 선택된 핫바 인덱스(0~8)
        java.util.UUID id = LinkIdUtil.getLinkId(tossed);

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            var eq = cap.getEquipment();

            boolean clearedByLink = false;
            if (id != null) {
                for (int slot : new int[] {
                        PlayerEquipment.SLOT_PRIM1,
                        PlayerEquipment.SLOT_PRIM2,
                        PlayerEquipment.SLOT_SEC,
                        PlayerEquipment.SLOT_MELEE
                }) {
                    ItemStack s = eq.getStackInSlot(slot);
                    if (!s.isEmpty() && s.hasTag() && s.getTag().hasUUID("link_id")
                            && id.equals(s.getTag().getUUID("link_id"))) {
                        eq.setStackInSlot(slot, ItemStack.EMPTY);
                        clearedByLink = true;
                    }
                }
            }

            // link_id가 없거나 위에서 못 지운 경우: 선택된 핫바 인덱스 ↔ 장비칸 매핑으로 보정
            if (!clearedByLink && selected >= 0 && selected <= 3) {
                int mappedSlot = switch (selected) {
                    case 0 -> PlayerEquipment.SLOT_PRIM1;
                    case 1 -> PlayerEquipment.SLOT_PRIM2;
                    case 2 -> PlayerEquipment.SLOT_SEC;
                    default -> PlayerEquipment.SLOT_MELEE;
                };
                ItemStack s = eq.getStackInSlot(mappedSlot);
                if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, tossed)) {
                    eq.setStackInSlot(mappedSlot, ItemStack.EMPTY);
                }
            }

            cap.applyWeaponsToHotbar(sp);
            SyncEquipmentPacketHandler.syncToClient(sp, cap);
        });
    }

    /* === 내부 유틸 === */
    private static void callUtilitySync(PlayerEquipment cap, ServerPlayer sp) {
        try {
            // PlayerEquipment#syncUtilityHotbar(ServerPlayer)
            var m = cap.getClass().getMethod("syncUtilityHotbar", ServerPlayer.class);
            m.invoke(cap, sp);
        } catch (NoSuchMethodException nsme) {
            try {
                // PlayerEquipment#tickMirrorUtilityHotbar(ServerPlayer)
                var m2 = cap.getClass().getMethod("tickMirrorUtilityHotbar", ServerPlayer.class);
                m2.invoke(cap, sp);
            } catch (Exception ignore) {
                // 둘 다 없으면 아무 것도 안 함
            }
        } catch (Exception ignore) {
            // invoke 예외 무시 (로그 필요하면 여기서 출력)
        }
    }
}
