package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NetworkInit {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(WeaponHotkeyPacket::register);
    }
}
