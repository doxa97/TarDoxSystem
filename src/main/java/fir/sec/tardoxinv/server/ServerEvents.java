package fir.sec.tardoxinv.server;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber
public class ServerEvents {

    /* useCustomInventory가 TRUE면, 사망 관련 3개 룰을 강제로 TRUE로 맞춰준다. */
    private static void ensureDeathRulesFollowUse(MinecraftServer server) {
        if (server == null) return;
        var rules = server.getGameRules();
        boolean use = rules.getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (use) {
            rules.getRule(GameRuleRegister.KEEP_UTILITY_BINDINGS_ON_DEATH).set(true, server);
            rules.getRule(GameRuleRegister.DROP_BACKPACK_ON_DEATH).set(true, server);
            rules.getRule(GameRuleRegister.DROP_EQUIPMENT_ON_DEATH).set(true, server);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent e) {
        ensureDeathRulesFollowUse(e.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        ensureDeathRulesFollowUse(sp.getServer());

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
            cap.tickMirrorUtilityHotbar(sp);
            if (cap.isDirty()) {
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
                cap.clearDirty();
            }
        });
    }

    // 드롭 차단 제거: 장비칸 정리만 유지
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!use) return;

        ItemStack tossed = e.getEntity().getItem();
        var id = LinkIdUtil.getLinkId(tossed);
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

    // 기능 6: 사망 시 드롭/보존 (useCustomInventory가 TRUE면 항상 활성화)
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e){
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        var rules = sp.getServer().getGameRules();
        boolean use = rules.getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);

        // useCustomInventory가 TRUE면 무조건 활성화되도록 OR 조건으로 강제
        boolean dropBackpack = use || rules.getBoolean(GameRuleRegister.DROP_BACKPACK_ON_DEATH);
        boolean dropEquip    = use || rules.getBoolean(GameRuleRegister.DROP_EQUIPMENT_ON_DEATH);

        if (!use) return; // 커스텀 인벤 비활성화라면 기존 바닐라 처리

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            if (dropBackpack) {
                ItemStack cur = cap.getBackpackItem();
                if (!cur.isEmpty()) {
                    ItemStack out = cur.copy();
                    var data = new net.minecraft.nbt.CompoundTag();
                    data.putInt("Width",  cap.getBackpackWidth());
                    data.putInt("Height", cap.getBackpackHeight());
                    data.put("Items",     cap.getBackpack2D().serializeNBT());
                    out.getOrCreateTag().put("BackpackData", data);

                    ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), out);
                    ent.setPickUpDelay(40);
                    sp.level().addFreshEntity(ent);

                    cap.setBackpackItem(ItemStack.EMPTY);
                    cap.resizeBackpack(0,0);
                }
            }
            if (dropEquip) {
                for (int i=0;i<PlayerEquipment.EQUIP_SLOTS;i++){
                    ItemStack s = cap.getEquipment().getStackInSlot(i);
                    if (!s.isEmpty()) {
                        ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY()+0.5, sp.getZ(), s.copy());
                        ent.setPickUpDelay(40);
                        sp.level().addFreshEntity(ent);
                        cap.getEquipment().setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        });
    }
}
