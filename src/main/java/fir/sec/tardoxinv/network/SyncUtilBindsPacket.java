package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncUtilBindsPacket {
    public byte[] storage = new byte[PlayerEquipment.HOTBAR_COUNT];
    public int[]  index   = new int[PlayerEquipment.HOTBAR_COUNT];

    // ★ 새로 추가: 서버에서 보낼 때 편의용
    public SyncUtilBindsPacket(byte[] storage, int[] index) {
        this.storage = storage;
        this.index   = index;
    }

    public SyncUtilBindsPacket() {} // 디코더용

    public static void encode(SyncUtilBindsPacket msg, FriendlyByteBuf buf) {
        for (int i = 0; i < PlayerEquipment.HOTBAR_COUNT; i++) buf.writeByte(msg.storage[i]);
        for (int i = 0; i < PlayerEquipment.HOTBAR_COUNT; i++) buf.writeVarInt(msg.index[i]);
    }

    public static SyncUtilBindsPacket decode(FriendlyByteBuf buf) {
        SyncUtilBindsPacket p = new SyncUtilBindsPacket();
        for (int i = 0; i < PlayerEquipment.HOTBAR_COUNT; i++) p.storage[i] = buf.readByte();
        for (int i = 0; i < PlayerEquipment.HOTBAR_COUNT; i++) p.index[i]   = buf.readVarInt();
        return p;
    }

    public static void handle(SyncUtilBindsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                for (int hb = 0; hb < PlayerEquipment.HOTBAR_COUNT; hb++) {
                    if (msg.storage[hb] < 0) {
                        cap.clientSetBinding(hb, null, -1);
                        continue;
                    }
                    PlayerEquipment.Storage s = (msg.storage[hb] == 0)
                            ? PlayerEquipment.Storage.BASE : PlayerEquipment.Storage.BACKPACK;
                    cap.clientSetBinding(hb, s, msg.index[hb]);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
