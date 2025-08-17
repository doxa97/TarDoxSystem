package fir.sec.tardoxinv.client.input;

import fir.sec.tardoxinv.network.WeaponHotkeyPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WeaponHotkeys {

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        if (Minecraft.getInstance().screen != null) return; // GUI 열려있으면 무시

        int hk = switch (e.getKey()) {
            case GLFW.GLFW_KEY_1 -> 0; // 주1
            case GLFW.GLFW_KEY_2 -> 1; // 주2
            case GLFW.GLFW_KEY_3 -> 2; // 보조
            case GLFW.GLFW_KEY_4 -> 3; // 근접
            default -> -1;
        };
        if (hk < 0) return;

        WeaponHotkeyPacket.send(hk);
    }
}
