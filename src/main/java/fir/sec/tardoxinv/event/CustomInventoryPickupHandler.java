package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.item.ModItems;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class CustomInventoryPickupHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;

        boolean useCustom = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
        if (!useCustom) return;

        ItemStack picked = event.getItem().getItem();
        String slotType = picked.hasTag() ? picked.getTag().getString("slot_type") : "";

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            LinkIdUtil.ensureLinkId(picked);
            ItemStack toPlace = picked.copy();
            boolean added = false;

            boolean isBackpackItem =
                    picked.is(ModItems.SMALL_BACKPACK.get()) ||
                            picked.is(ModItems.MEDIUM_BACKPACK.get()) ||
                            picked.is(ModItems.LARGE_BACKPACK.get()) ||
                            "backpack".equals(slotType);

            if (isBackpackItem) {
                if (cap.getBackpackWidth() == 0 && cap.getBackpackItem().isEmpty()) {
                    var tag = toPlace.getOrCreateTag();
                    if (tag.getInt("Width") <= 0 || tag.getInt("Height") <= 0) {
                        if (picked.is(ModItems.SMALL_BACKPACK.get())) { tag.putInt("Width",2); tag.putInt("Height",4); }
                        else if (picked.is(ModItems.MEDIUM_BACKPACK.get())) { tag.putInt("Width",3); tag.putInt("Height",5); }
                        else if (picked.is(ModItems.LARGE_BACKPACK.get())) { tag.putInt("Width",4); tag.putInt("Height",6); }
                        tag.putString("slot_type","backpack");
                    }
                    cap.setBackpackItem(toPlace);
                    added = true;
                } else {
                    // 배낭 장비 중이면 기본 2x2 → 배낭칸으로 시도 (멀티칸)
                    added = cap.getBase2x2().insertGrid(toPlace) || cap.getBackpack2D().insertGrid(toPlace);
                }
            } else if (!slotType.isEmpty()) {
                // 장비칸 우선 시도
                added = tryEquip(cap, toPlace, slotType);
                if (!added) added = cap.getBase2x2().insertGrid(toPlace) || cap.getBackpack2D().insertGrid(toPlace);
            } else {
                added = cap.getBase2x2().insertGrid(toPlace) || cap.getBackpack2D().insertGrid(toPlace);
            }

            if (added) {
                event.getItem().discard();
                event.setCanceled(true);
                cap.applyWeaponsToHotbar(sp);
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            }
        });
    }

    private static boolean tryEquip(PlayerEquipment cap, ItemStack stack, String type) {
        var eq = cap.getEquipment();
        PlayerEquipment.ensureLink(stack);
        switch (type) {
            case "primary_weapon" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_PRIM1).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_PRIM1, stack); return true; }
                if (eq.getStackInSlot(PlayerEquipment.SLOT_PRIM2).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_PRIM2, stack); return true; }
            }
            case "secondary_weapon" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_SEC).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_SEC, stack); return true; }
            }
            case "melee_weapon" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_MELEE).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_MELEE, stack); return true; }
            }
            case "helmet" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_HELMET).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_HELMET, stack); return true; }
            }
            case "vest" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_VEST).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_VEST, stack); return true; }
            }
            case "headset" -> {
                if (eq.getStackInSlot(PlayerEquipment.SLOT_HEADSET).isEmpty()) { eq.setStackInSlot(PlayerEquipment.SLOT_HEADSET, stack); return true; }
            }
        }
        return false;
    }
}
