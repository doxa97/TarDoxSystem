package fir.sec.tardoxinv.event;

import fir.sec.tardoxinv.client.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class PerspectiveBlocker {

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (!ClientState.USE_CUSTOM_INVENTORY) return;
        if (e.getAction() != 1) return; // PRESS
        // 취소 호출 금지(이 이벤트는 cancel 불가) → 그냥 무시만
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!ClientState.USE_CUSTOM_INVENTORY) return;

        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.options.getCameraType() != CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.FIRST_PERSON); // 항상 1인칭 유지
        }
    }
}
