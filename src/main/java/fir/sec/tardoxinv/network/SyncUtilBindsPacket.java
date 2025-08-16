package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncUtilBindsPacket {
    // hb(5개), storage(0=base/1=backpack/-1없음), index
    public final byte[] storage = new byte[5];
    public final int[]  index   = new int[5];

    public SyncUtilBindsPacket() {}

    public static void encode(SyncUtilBindsPacket msg, FriendlyByteBuf buf) {
        for (int i=0;i<5;i++){ buf.writeByte(msg.storage[i]); buf.writeVarInt(msg.index[i]); }
    }
    public static SyncUtilBindsPacket decode(FriendlyByteBuf buf) {
        SyncUtilBindsPacket p = new SyncUtilBindsPacket();
        for (int i=0;i<5;i++){ p.storage[i]=buf.readByte(); p.index[i]=buf.readVarInt(); }
        return p;
    }

    public static void handle(SyncUtilBindsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player==null) return;
            mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                for (int i=0;i<5;i++){
                    int hb = 4 + i;
                    if (msg.storage[i] < 0) { cap.clientSetBinding(hb, null, -1); continue; }
                    PlayerEquipment.Storage s = (msg.storage[i]==0 ? PlayerEquipment.Storage.BASE : PlayerEquipment.Storage.BACKPACK);
                    cap.clientSetBinding(hb, s, msg.index[i]);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
