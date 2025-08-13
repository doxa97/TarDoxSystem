package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.client.ClientState;
import fir.sec.tardoxinv.network.RotateCarriedPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class RotateKeyHandler {
    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (!ClientState.USE_CUSTOM_INVENTORY) return;
        if (e.getAction() != 1) return; // PRESS
        if (e.getKey() == GLFW.GLFW_KEY_R) {
            SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateCarriedPacket());
        }
    }
}
