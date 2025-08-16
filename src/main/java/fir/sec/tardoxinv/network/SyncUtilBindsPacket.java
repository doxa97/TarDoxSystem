package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.client.ClientHotbarBindings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 서버→클라: 유틸 바인딩 스냅샷 전달 */
public class SyncUtilBindsPacket {
    private final byte[] storageByHb;
    private final int[]  indexByHb;

    public SyncUtilBindsPacket(byte[] storageByHb, int[] indexByHb) {
        this.storageByHb = storageByHb;
        this.indexByHb = indexByHb;
    }

    public static void encode(SyncUtilBindsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.storageByHb.length);
        for (byte b : msg.storageByHb) buf.writeByte(b);
        buf.writeVarInt(msg.indexByHb.length);
        for (int i : msg.indexByHb) buf.writeVarInt(i);
    }

    public static SyncUtilBindsPacket decode(FriendlyByteBuf buf) {
        int n1 = buf.readVarInt();
        byte[] st = new byte[n1];
        for (int i = 0; i < n1; i++) st[i] = buf.readByte();
        int n2 = buf.readVarInt();
        int[] idx = new int[n2];
        for (int i = 0; i < n2; i++) idx[i] = buf.readVarInt();
        return new SyncUtilBindsPacket(st, idx);
    }

    public static void handle(SyncUtilBindsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHotbarBindings.setAll(msg.storageByHb, msg.indexByHb)
                )
        );
        ctx.get().setPacketHandled(true);
    }
}
