package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
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
            // 1) 무기 핫바(0~3) → 장비칸 동기화(연동)
            reconcileWeapons(sp, cap);

            // 2) 유틸(5~9) 동기화(버전별 메서드명 호환)
            callUtilitySync(cap, sp);

            // 3) 장비칸 → 핫바(1~4) 미러 갱신
            cap.applyWeaponsToHotbar(sp);

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
        int selected = sp.getInventory().selected; // 0~8
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

    // 핫바(0~3)의 변경(사용, 내구도, 제거 등)을 장비칸에 반영
    private static void reconcileWeapons(ServerPlayer sp, PlayerEquipment cap) {
        var inv = sp.getInventory();
        var eq  = cap.getEquipment();

        int[] map = new int[] {
                PlayerEquipment.SLOT_PRIM1,
                PlayerEquipment.SLOT_PRIM2,
                PlayerEquipment.SLOT_SEC,
                PlayerEquipment.SLOT_MELEE
        };

        for (int i = 0; i < 4; i++) {
            ItemStack hb = inv.getItem(i);
            ItemStack es = eq.getStackInSlot(map[i]);

            java.util.UUID hId = LinkIdUtil.getLinkId(hb);
            java.util.UUID eId = LinkIdUtil.getLinkId(es);

            // (A) 둘 다 비어있으면 패스
            if (hb.isEmpty() && es.isEmpty()) continue;

            // (B) 핫바 비었고 장비칸 존재 → 장비칸 제거
            if (hb.isEmpty() && !es.isEmpty()) {
                eq.setStackInSlot(map[i], ItemStack.EMPTY);
                continue;
            }

            // (C) 장비칸 비었고 핫바 존재 → 장비칸에 채움 (신규 링크 보정)
            if (!hb.isEmpty() && es.isEmpty()) {
                LinkIdUtil.ensureLinkId(hb);
                eq.setStackInSlot(map[i], hb.copy());
                continue;
            }

            // (D) 둘 다 존재: 같은 link_id면 핫바 변경(내구/수량/NBT)을 장비칸으로 업서트
            if (hId != null && eId != null && hId.equals(eId)) {
                eq.setStackInSlot(map[i], hb.copy());
            } else {
                // 링크 다르면: 장비칸을 핫바 것으로 교체(사용자가 바꿨다고 판단)
                LinkIdUtil.ensureLinkId(hb);
                eq.setStackInSlot(map[i], hb.copy());
            }
        }
    }

    // syncUtilityHotbar(ServerPlayer) 또는 tickMirrorUtilityHotbar(ServerPlayer) 호출 (버전 호환)
    private static void callUtilitySync(PlayerEquipment cap, ServerPlayer sp) {
        try {
            var m = cap.getClass().getMethod("syncUtilityHotbar", ServerPlayer.class);
            m.invoke(cap, sp);
            return;
        } catch (NoSuchMethodException ignore) { /* 다음 후보 */ }
        catch (Exception ignore) { return; }

        try {
            var m2 = cap.getClass().getMethod("tickMirrorUtilityHotbar", ServerPlayer.class);
            m2.invoke(cap, sp);
        } catch (Exception ignore) { }
    }
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.isCanceled()) return; // 🔹 이미 다른 핸들러가 처리했으면 스킵
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        ItemEntity itemEnt = event.getItem();
        ItemStack stack = itemEnt.getItem();
        if (stack.isEmpty()) return;

        player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            GridItemHandler2D base = eq.getBase2x2();     // 프로젝트의 실제 getter 이름에 맞춰주세요
            GridItemHandler2D pack = eq.getBackpack2D();    // 동일

            ItemStack remain = stack.copy();
            if (base != null) remain = base.insertAnywhere(remain, false);
            if (!remain.isEmpty() && pack != null) remain = pack.insertAnywhere(remain, false);

            // 일부/전량이 들어갔으면 기본 픽업은 막고, 월드 아이템을 갱신
            if (remain.getCount() != stack.getCount()) {
                int picked = stack.getCount() - (remain.isEmpty() ? 0 : remain.getCount());
                if (remain.isEmpty()) {
                    itemEnt.discard();                     // 전량 수납 → 월드 아이템 제거
                } else {
                    itemEnt.setItem(remain);               // 일부 수납 → 남은 수량만 월드에 유지
                }
                event.setCanceled(true);
                player.take(itemEnt, picked);              // 픽업 애니/사운드 처리
            }
        });
    }
}
