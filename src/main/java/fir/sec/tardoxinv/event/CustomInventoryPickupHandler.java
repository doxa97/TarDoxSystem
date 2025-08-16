package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
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
        if (picked.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            LinkIdUtil.ensureLinkId(picked);
            ItemStack toPlace = picked.copy();
            boolean added = false;

            // 배낭류(직접 장착) 판별
            boolean isBackpackItem =
                    toPlace.is(ModItems.SMALL_BACKPACK.get()) ||
                            toPlace.is(ModItems.MEDIUM_BACKPACK.get()) ||
                            toPlace.is(ModItems.LARGE_BACKPACK.get()) ||
                            (toPlace.hasTag() && "backpack".equals(toPlace.getTag().getString("slot_type")));

            if (isBackpackItem) {
                if (cap.getBackpackWidth() == 0 && cap.getBackpackItem().isEmpty()) {
                    var tag = toPlace.getOrCreateTag();
                    if (tag.getInt("Width") <= 0 || tag.getInt("Height") <= 0) {
                        if (toPlace.is(ModItems.SMALL_BACKPACK.get())) { tag.putInt("Width",2); tag.putInt("Height",4); }
                        else if (toPlace.is(ModItems.MEDIUM_BACKPACK.get())) { tag.putInt("Width",3); tag.putInt("Height",5); }
                        else if (toPlace.is(ModItems.LARGE_BACKPACK.get())) { tag.putInt("Width",4); tag.putInt("Height",6); }
                        tag.putString("slot_type","backpack");
                    }
                    cap.setBackpackItem(toPlace);
                    added = true;
                } else {
                    added = tryAddToBaseFirstOrBackpack(cap, toPlace);
                }
            } else {
                // 일반 아이템: 1x1은 기본 2x2 먼저, 다칸은 배낭 우선
                added = tryAddToBaseFirstOrBackpack(cap, toPlace);
            }

            if (added) {
                // 성공한 경우에만 엔티티 제거/취소
                event.getItem().discard();
                event.setCanceled(true);

                cap.applyWeaponsToHotbar(sp);
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            }
            // 실패 시 바닐라로 넘겨서 증발 방지(취소 X, discard X)
        });
    }

    private static boolean tryAddToBaseFirstOrBackpack(PlayerEquipment cap, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int w = stack.hasTag() ? Math.max(1, stack.getTag().getInt("Width")) : 1;
        int h = stack.hasTag() ? Math.max(1, stack.getTag().getInt("Height")) : 1;

        // 1x1 → 기본 2x2 우선
        if (w == 1 && h == 1) {
            var base = cap.getBase2x2();
            for (int i = 0; i < base.getSlots(); i++) {
                if (base.getStackInSlot(i).isEmpty()) {
                    base.setStackInSlot(i, stack.copy());
                    return true;
                }
            }
            return cap.getBackpack2D().tryPlaceFirstFit(stack);
        }
        // 다칸 → 배낭
        return cap.getBackpack2D().tryPlaceFirstFit(stack);
    }
}
