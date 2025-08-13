package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, value = Dist.CLIENT)
public class InventoryInterceptor {
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof InventoryScreen) {
            // 서버가 gamerule true면 커스텀 인벤을 열 것이고, false면 무시되어 바닐라 유지
            SyncEquipmentPacketHandler.sendOpenEquipment(0, 0);
        }
    }
}
