package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.item.ModItems;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.SimpleMenuProvider;

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
                    ensureBackpackDefaults(toPlace);
                    cap.setBackpackItem(toPlace);

                    if (toPlace.hasTag() && toPlace.getTag().contains("BackpackData")) {
                        restoreBackpackContents(cap, toPlace.getTag().getCompound("BackpackData"));
                    }
                    added = true;

                    if (sp.containerMenu instanceof EquipmentMenu) {
                        int bw = cap.getBackpackWidth(), bh = cap.getBackpackHeight();
                        NetworkHooks.openScreen(
                                sp,
                                new SimpleMenuProvider(
                                        (id, inv, ply) -> new EquipmentMenu(id, inv, bw, bh),
                                        Component.literal("Equipment")
                                ),
                                buf -> { buf.writeVarInt(bw); buf.writeVarInt(bh); }
                        );
                    }
                } else {
                    added = placeInBackpackGrid(cap, toPlace);
                    if (!added) added = tryAddToBase2x2(cap, toPlace);
                }
            } else {
                if (!slotType.isEmpty()) {
                    added = tryEquip(cap, toPlace, slotType);
                }
                if (!added) added = placeInBackpackGrid(cap, toPlace);
                if (!added) {
                    int w = getW(toPlace), h = getH(toPlace);
                    if (w == 1 && h == 1) added = tryAddToBase2x2(cap, toPlace);
                }
                if (!added) {
                    sp.displayClientMessage(Component.literal("배낭에 공간이 부족합니다."), true);
                }
            }

            if (added) {
                event.getItem().discard();
                event.setCanceled(true);
                cap.applyWeaponsToHotbar(sp);
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            }
        });
    }

    /* ---- 유틸 ---- */

    private static void ensureBackpackDefaults(ItemStack stack) {
        var tag = stack.getOrCreateTag();
        if (tag.getInt("Width") <= 0)  tag.putInt("Width",  2);
        if (tag.getInt("Height") <= 0) tag.putInt("Height", 4);
        tag.putString("slot_type", "backpack");
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

    private static boolean tryAddToBase2x2(PlayerEquipment cap, ItemStack stack) {
        var base = cap.getBase2x2();
        for (int i = 0; i < base.getSlots(); i++) {
            if (base.getStackInSlot(i).isEmpty()) { base.setStackInSlot(i, stack); return true; }
        }
        return false;
    }

    private static boolean placeInBackpackGrid(PlayerEquipment cap, ItemStack stack) {
        int itemW = getW(stack), itemH = getH(stack);
        int BW = cap.getBackpackWidth(), BH = cap.getBackpackHeight();
        if (BW <= 0 || BH <= 0) return false;

        var handler = tryGetBackpackHandler(cap);
        if (handler == null) return false;

        int size = handler.getSlots(); // 보통 BW*BH
        for (int anchor = 0; anchor < size; anchor++) {
            int ax = anchor % BW, ay = anchor / BW;
            if (ax + itemW > BW || ay + itemH > BH) continue;

            boolean ok = true;
            for (int dx = 0; dx < itemW && ok; dx++) {
                for (int dy = 0; dy < itemH; dy++) {
                    int nid = (ax + dx) + (ay + dy) * BW;
                    if (nid < 0 || nid >= size) { ok = false; break; }
                    if (!handler.getStackInSlot(nid).isEmpty()) { ok = false; break; }
                }
            }
            if (!ok) continue;

            handler.setStackInSlot(anchor, stack);
            return true;
        }
        return false;
    }

    private static void restoreBackpackContents(PlayerEquipment cap, net.minecraft.nbt.CompoundTag data) {
        int w = Math.max(0, data.getInt("Width"));
        int h = Math.max(0, data.getInt("Height"));
        cap.resizeBackpack(w, h);

        var itemsNbt = data.getCompound("Items");
        var handler = tryGetBackpackHandler(cap);
        if (handler != null) {
            try { handler.deserializeNBT(itemsNbt); } catch (Exception ignored) {}
        }
    }

    private static int getW(ItemStack st) {
        return st.hasTag() ? Math.max(1, st.getTag().getInt("Width")) : 1;
    }
    private static int getH(ItemStack st) {
        return st.hasTag() ? Math.max(1, st.getTag().getInt("Height")) : 1;
    }

    /** 2D/1D 어떤 구현이건 리플렉션으로 안전 획득 */
    private static net.minecraftforge.items.ItemStackHandler tryGetBackpackHandler(PlayerEquipment cap) {
        try {
            var m = cap.getClass().getMethod("getBackpack2D");
            return (net.minecraftforge.items.ItemStackHandler) m.invoke(cap);
        } catch (Exception ignore) { }
        try {
            var m = cap.getClass().getMethod("getBackpack");
            return (net.minecraftforge.items.ItemStackHandler) m.invoke(cap);
        } catch (Exception ignore) { }
        return null;
    }
}
