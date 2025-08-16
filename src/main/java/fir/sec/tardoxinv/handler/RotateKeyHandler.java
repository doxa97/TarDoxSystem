package fir.sec.tardoxinv.handler;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.network.RotateSlotPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * R 키 입력 → 커서에 들고 있는 아이템 회전 요청(무인자 패킷).
 * KeyMapping 의존성을 피해서 직접 GLFW 키코드 검사.
 */
@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, value = Dist.CLIENT)
public class RotateKeyHandler {

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        // 키가 눌린 순간만 처리
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        // 기본 키: R
        if (event.getKey() == GLFW.GLFW_KEY_R) {
            // 무인자 패킷 전송(encode/decode payload 없음)
            SyncEquipmentPacketHandler.CHANNEL.sendToServer(new RotateSlotPacket());
        }
    }
}
