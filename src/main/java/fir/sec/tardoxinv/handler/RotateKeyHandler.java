package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.client.ClientState;
import fir.sec.tardoxinv.network.RotateCarriedPacket;
import fir.sec.tardoxinv.network.RotateSlotPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> scr) {
                var slot = scr.getSlotUnderMouse();
                if (slot != null) {
                    int menuSlotId = scr.getMenu().slots.indexOf(slot);
                    if (menuSlotId >= 0) {
                        SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateSlotPacket(menuSlotId));
                        return;
                    }
                }
                // 슬롯이 없으면 커서 회전
                SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateCarriedPacket());
                return;
            }
            // 장비창 아닐 때는 커서 회전만
            SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateCarriedPacket());
        }
    }
}
