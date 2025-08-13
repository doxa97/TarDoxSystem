package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.client.ClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncGamerulePacket {
    private final boolean useCustom;

    public SyncGamerulePacket(boolean useCustom) { this.useCustom = useCustom; }

    public static void encode(SyncGamerulePacket msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.useCustom); }
    public static SyncGamerulePacket decode(FriendlyByteBuf buf) { return new SyncGamerulePacket(buf.readBoolean()); }

    public static void handle(SyncGamerulePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientState.USE_CUSTOM_INVENTORY = msg.useCustom);
        ctx.get().setPacketHandled(true);
    }
}
