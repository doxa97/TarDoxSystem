package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.capability.ModCapabilities;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class HotbarUseSyncHandler {
    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
        // 필요 시: 유틸리티 아이템 사용 시 커스텀 재고에서 소모 동기화 로직 확장
        // 현재는 별도 처리 생략 (능동 소비 로직은 아이템 자체의 use/finishUsingItem에 위임 권장)
    }
}
