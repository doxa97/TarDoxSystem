package fir.sec.tardoxinv.screen;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientMenuScreens {
    private ClientMenuScreens() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> MenuScreens.register(ModMenus.EQUIPMENT_MENU.get(), EquipmentScreen::new));
    }
}
