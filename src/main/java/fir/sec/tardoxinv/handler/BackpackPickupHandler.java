package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.item.BackpackItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID)
public class BackpackPickupHandler {

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemStack picked = event.getItem().getItem();

        if (picked.getItem() instanceof BackpackItem) {
            // TODO: 실제 장착칸에 적용하는 Capability 로직 연결 필요
            // 임시: 인벤토리 첫 칸에 강제 배낭 장착
            if (!player.getInventory().contains(picked)) {
                player.getInventory().add(picked.copy());
                picked.shrink(1);
            }
        }
    }
}
