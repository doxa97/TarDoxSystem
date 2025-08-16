package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class CustomInventoryPickupHandler {

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent e) {
        Player p = e.getEntity();
        if (!(p instanceof ServerPlayer sp)) return;

        ItemEntity ent = e.getItem();
        ItemStack stack = ent.getItem();
        if (stack.isEmpty()) return;

        sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            // 1) 배낭 중복 습득 방지 + 장착 즉시 갱신
            boolean isBackpackItem = isBackpack(stack);
            if (isBackpackItem) {
                if (!cap.getBackpackItem().isEmpty()) {
                    // 이미 배낭이 있으면 이번 픽업은 커스텀 장착 로직을 막고 바닐라 처리 그대로 진행
                    return;
                }
                // 첫 배낭 장착
                ItemStack one = stack.split(1);
                cap.setBackpackItem(one);
                int w = readSize(one, "Width");
                int h = readSize(one, "Height");
                cap.resizeBackpack(Math.max(0, w), Math.max(0, h));
                cap.remapBackpackBindingsAfterResize();
                // 서버->클라 전체 동기화 (인벤토리 열려있어도 즉시 그리드가 보이게)
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
                // 엔티티 아이템 정리
                if (stack.isEmpty()) ent.discard();
                e.setCanceled(true);
                return;
            }

            // 2) 2x2(혹은 그 이상) 아이템: "되돌려두기" 실패/드롭 크래시 방지
            //   픽업 시 커스텀 그리드로 먼저 삽입 시도 -> 실패하면 바닐라에 맡김
            if (hasAnySize(stack)) {
                ItemStack remain = tryInsertIntoCustomInventory(cap, stack);
                if (remain.getCount() != stack.getCount()) {
                    // 일부 혹은 전부 들어갔음 — 엔티티 갱신
                    if (remain.isEmpty()) {
                        ent.discard();
                        e.setCanceled(true);
                        SyncEquipmentPacketHandler.syncToClient(sp, cap);
                        return;
                    } else {
                        ent.setItem(remain);
                        e.setCanceled(true);
                        SyncEquipmentPacketHandler.syncToClient(sp, cap);
                        return;
                    }
                }
            }
        });
    }

    private static boolean isBackpack(ItemStack st) {
        // 배낭 판정: 태그 혹은 아이템 자체로 판정하는 프로젝트 규칙에 맞춰 태그 우선
        CompoundTag t = st.getTag();
        if (t != null && (t.contains("Width") || t.contains("Height"))) return true;
        // 추가 규칙이 있으면 여기에 (예: st.is(ModTags.BACKPACK) 등)
        return false;
    }

    private static boolean hasAnySize(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && (t.contains("Width") || t.contains("Height"));
    }

    private static int readSize(ItemStack st, String key) {
        CompoundTag t = st.getTag();
        return (t != null && t.contains(key)) ? Math.max(0, t.getInt(key)) : 0;
    }

    /**
     * 커스텀 인벤토리(기본2x2 + 배낭)에 삽입 시도
     */
    private static ItemStack tryInsertIntoCustomInventory(PlayerEquipment cap, ItemStack stack) {
        ItemStack work = stack.copy();

        // 기본 2x2 먼저
        var base = (fir.sec.tardoxinv.capability.GridItemHandler2D) cap.getBase2x2();
        work = base.insertItem2D(work, false);
        if (work.isEmpty()) return ItemStack.EMPTY;

        // 배낭 존재 시 배낭에도 시도
        if (!cap.getBackpackItem().isEmpty() && cap.getBackpackWidth() > 0 && cap.getBackpackHeight() > 0) {
            var bp = (fir.sec.tardoxinv.capability.GridItemHandler2D) cap.getBackpack();
            work = bp.insertItem2D(work, false);
        }
        return work;
    }
}
