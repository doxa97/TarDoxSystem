package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 습득 우선순위:
 * 1) "배낭 아이템"이면: 빈 배낭 장착칸에 장착 + 내부 복원 (핫바로 흡수 금지)
 * 2) 일반 아이템이면: 기본 2x2 → 배낭 그리드
 * 3) 둘 다 실패 → 바닐라 처리
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomInventoryPickupHandler {

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent e) {
        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;

        ItemEntity ie = e.getItem();
        ItemStack stack = ie.getItem();
        if (stack.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
            // 1) 배낭 아이템?
            if (isBackpackItem(stack)) {
                // 이미 배낭 착용 중이면: 기본/배낭 그리드로 삽입 시도 (장착칸 중복 금지)
                if (!eq.getBackpackItem().isEmpty()) {
                    if (tryInsertInto(eq.getBase2x2(), stack) || tryInsertInto(eq.getBackpack2D(), stack)) {
                        ie.discard(); e.setCanceled(true); SyncEquipmentPacketHandler.syncToClient(sp, eq);
                    }
                    return;
                }

                // 빈 배낭 장착칸에 장착 + 그리드 복원/생성
                CompoundTag data = stack.getTag() != null ? stack.getTag().getCompound("BackpackData") : null;
                int w = (data != null && data.contains("W")) ? data.getInt("W") : Math.max(0, stack.getOrCreateTag().getInt("BackpackW"));
                int h = (data != null && data.contains("H")) ? data.getInt("H") : Math.max(0, stack.getOrCreateTag().getInt("BackpackH"));

                // 최소 0~32 정도로 클램프
                w = Math.max(0, Math.min(32, w));
                h = Math.max(0, Math.min(32, h));

                eq.setBackpackItem(stack.copy());
                eq.resizeBackpack(w, h);

                if (data != null && data.contains("Items")) {
                    eq.getBackpack2D().deserializeNBT(data.getCompound("Items")); // 내부 아이템 복원
                }

                ie.discard();
                e.setCanceled(true);
                // 화면 재오픈(새 그리드 적용)
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                SyncEquipmentPacketHandler.openEquipmentScreen(sp, w, h);
                return;
            }

            // 2) 일반 아이템: 기본 2x2 → 배낭
            if (tryInsertInto(eq.getBase2x2(), stack) || tryInsertInto(eq.getBackpack2D(), stack)) {
                ie.discard(); e.setCanceled(true); SyncEquipmentPacketHandler.syncToClient(sp, eq);
            }
        });
    }

    private static boolean tryInsertInto(GridItemHandler2D gh, ItemStack st) {
        if (gh == null || st.isEmpty()) return false;
        for (int i = 0; i < gh.getSlots(); i++) {
            if (gh.canPlaceAt(i, st)) {
                gh.insertItem2D(i, st.copy(), false);
                return true;
            }
        }
        return false;
    }

    /** 배낭 아이템 판정:
     *  - DropBackpackPacket이 저장한 "BackpackData" NBT 가 있거나,
     *  - 아이템 NBT에 BackpackW/BackpackH 키가 있거나,
     *  - (보조) descriptionId 에 "backpack" 포함
     */
    private static boolean isBackpackItem(ItemStack st) {
        var t = st.getTag();
        if (t != null && (t.contains("BackpackData") || t.contains("BackpackW") || t.contains("BackpackH"))) return true;
        String id = st.getItem().getDescriptionId().toLowerCase();
        return id.contains("backpack");
    }
}
