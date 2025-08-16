package fir.sec.tardoxinv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 클라→서버 : 회전 요청.
 * - 페이로드 없음. 서버는 '커서에 들고 있는 아이템'의 Width/Height를 스왑한다.
 * - 슬롯 위에 올려둔 상태까지는 지금은 지원하지 않는다(추가 패킷 필요).
 */
public class RotateSlotPacket {
    public static void encode(RotateSlotPacket msg, FriendlyByteBuf buf) {}
    public static RotateSlotPacket decode(FriendlyByteBuf buf) { return new RotateSlotPacket(); }

    public static void handle(RotateSlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            var carried = sp.containerMenu.getCarried();
            if (carried.isEmpty()) return;

            CompoundTag tag = carried.getOrCreateTag();
            int w = Math.max(1, tag.getInt("Width"));
            int h = Math.max(1, tag.getInt("Height"));

            // 스왑
            tag.putInt("Width",  h);
            tag.putInt("Height", w);

            sp.containerMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
