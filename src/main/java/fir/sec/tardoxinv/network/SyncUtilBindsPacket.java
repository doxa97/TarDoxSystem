package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncUtilBindsPacket {
    // 핫바 4..8 각각에 대해 storage(-1 none, 0 base, 1 backpack), index
    private final byte[] storage = new byte[5];
    private final int[] index = new int[5];

    public SyncUtilBindsPacket(byte[] storage, int[] index) {
        System.arraycopy(storage, 0, this.storage, 0, 5);
        System.arraycopy(index, 0, this.index, 0, 5);
    }

    public static void encode(SyncUtilBindsPacket msg, FriendlyByteBuf buf) {
        for (int i = 0; i < 5; i++) buf.writeByte(msg.storage[i]);
        for (int i = 0; i < 5; i++) buf.writeVarInt(msg.index[i]);
    }

    public static SyncUtilBindsPacket decode(FriendlyByteBuf buf) {
        byte[] s = new byte[5];
        int[] idx = new int[5];
        for (int i = 0; i < 5; i++) s[i] = buf.readByte();
        for (int i = 0; i < 5; i++) idx[i] = buf.readVarInt();
        return new SyncUtilBindsPacket(s, idx);
    }

    public static void handle(SyncUtilBindsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                for (int i = 0; i < 5; i++) {
                    int hb = 4 + i;
                    byte s = msg.storage[i];
                    if (s < 0) {
                        cap.clientSetBinding(hb, null, -1);
                    } else if (s == 0) {
                        cap.clientSetBinding(hb, PlayerEquipment.Storage.BASE, msg.index[i]);
                    } else {
                        cap.clientSetBinding(hb, PlayerEquipment.Storage.BACKPACK, msg.index[i]);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
