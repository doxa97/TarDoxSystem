package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.client.ClientState;
import fir.sec.tardoxinv.network.RotateCarriedPacket;
import fir.sec.tardoxinv.network.RotateSlotPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
        if (e.getAction() != 1) return;
        if (e.getKey() != GLFW.GLFW_KEY_R) return;

        var mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> scr) {
            Slot slot = scr.getSlotUnderMouse();
            if (slot != null) {
                ItemStack st = slot.getItem();
                if (!st.isEmpty() && st.hasTag() &&
                        st.getTag().contains("Width") && st.getTag().contains("Height")) {
                    int slotId = scr.getMenu().slots.indexOf(slot);
                    if (slotId >= 0) {
                        SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateSlotPacket(slotId));
                        return;
                    }
                }
            }
            ItemStack carried = scr.getMenu().getCarried();
            if (!carried.isEmpty() && carried.hasTag() &&
                    carried.getTag().contains("Width") && carried.getTag().contains("Height")) {
                SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateCarriedPacket());
            }
        }
    }
}
