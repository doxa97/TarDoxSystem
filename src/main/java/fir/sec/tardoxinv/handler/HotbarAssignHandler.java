package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.network.AssignFromSlotPacket;
import fir.sec.tardoxinv.network.AssignHotbarPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class HotbarAssignHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key e) {
        if (e.getAction() != 1) return; // PRESS

        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> scr)) return;

        int key = e.getKey();
        if (key < GLFW.GLFW_KEY_5 || key > GLFW.GLFW_KEY_9) return;

        int hotbarIndex = key - GLFW.GLFW_KEY_1; // 4..8

        // 1) 슬롯 위 유틸리티
        var slot = scr.getSlotUnderMouse();
        if (slot != null) {
            ItemStack st = slot.getItem();
            if (!st.isEmpty() && st.hasTag() && "utility".equals(st.getTag().getString("slot_type"))) {
                int slotId = scr.getMenu().slots.indexOf(slot);
                SyncEquipmentPacketHandler.CHANNEL.sendToServer(new AssignFromSlotPacket(slotId, hotbarIndex));
                return;
            }
        }

        // 2) 커서(carried) 유틸리티
        ItemStack carried = scr.getMenu().getCarried();
        if (!carried.isEmpty() && carried.hasTag() && "utility".equals(carried.getTag().getString("slot_type"))) {
            SyncEquipmentPacketHandler.CHANNEL.sendToServer(new AssignHotbarPacket(hotbarIndex));
        }
    }
}
