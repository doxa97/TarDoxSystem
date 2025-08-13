package fir.sec.tardoxinv.command;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import fir.sec.tardoxinv.TarDoxInv;

/**
 * 명령어 등록 이벤트 핸들러
 */
@Mod.EventBusSubscriber(modid = TarDoxInv.MODID)
public class CommandEvents {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        OpenEquipmentCommand.register(event.getDispatcher());
    }
}
