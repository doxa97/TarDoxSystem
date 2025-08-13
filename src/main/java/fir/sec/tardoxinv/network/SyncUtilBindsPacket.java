package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 핫바(5~9) 바인딩 스냅샷을 클라이언트로 동기화(오버레이 표시용) */
public class SyncUtilBindsPacket {
    // 각 엔트리: storage(-1 none, 0 base, 1 backpack), index
    private final byte[] storage = new byte[5];
    private final int[]  index   = new int[5];

    public SyncUtilBindsPacket(PlayerEquipment cap) {
        for (int i = 0; i < 5; i++) {
            int hb = 4 + i;
            PlayerEquipment.UtilBinding b = cap.peekBinding(hb);
            if (b == null) { storage[i] = -1; index[i] = -1; }
            else {
                storage[i] = (byte)(b.storage == PlayerEquipment.Storage.BASE ? 0 : 1);
                index[i]   = b.index;
            }
        }
    }

    public SyncUtilBindsPacket(byte[] storage, int[] index) {
        System.arraycopy(storage, 0, this.storage, 0, 5);
        System.arraycopy(index,   0, this.index,   0, 5);
    }

    public static void encode(SyncUtilBindsPacket msg, FriendlyByteBuf buf) {
        for (int i = 0; i < 5; i++) {
            buf.writeByte(msg.storage[i]);
            buf.writeVarInt(msg.index[i]);
        }
    }

    public static SyncUtilBindsPacket decode(FriendlyByteBuf buf) {
        byte[] st = new byte[5];
        int[]  ix = new int[5];
        for (int i = 0; i < 5; i++) {
            st[i] = buf.readByte();
            ix[i] = buf.readVarInt();
        }
        return new SyncUtilBindsPacket(st, ix);
    }

    public static void handle(SyncUtilBindsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                for (int i = 0; i < 5; i++) {
                    int hb = 4 + i;
                    byte st = msg.storage[i];
                    if (st < 0) {
                        cap.clientSetBinding(hb, null, -1);
                    } else if (st == 0) {
                        cap.clientSetBinding(hb, PlayerEquipment.Storage.BASE, msg.index[i]);
                    } else {
                        cap.clientSetBinding(hb, PlayerEquipment.Storage.BACKPACK, msg.index[i]);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /** 서버에서 즉시 스냅샷 보내기 */
    public static void send(ServerPlayer sp, PlayerEquipment cap) {
        SyncEquipmentPacketHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), new SyncUtilBindsPacket(cap));
    }
}
