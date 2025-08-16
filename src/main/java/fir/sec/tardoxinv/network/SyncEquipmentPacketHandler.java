package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class SyncEquipmentPacketHandler {

    public static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarDoxInv.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, SyncUtilBindsPacket.class, SyncUtilBindsPacket::encode, SyncUtilBindsPacket::decode, SyncUtilBindsPacket::handle);
        // 회전 패킷을 쓰는 경우(크래시 리포트 대비) 여기서 반드시 등록
        CHANNEL.registerMessage(id++, RotateSlotPacket.class, RotateSlotPacket::encode, RotateSlotPacket::decode, RotateSlotPacket::handle);
        // (필요시) 메뉴 새로고침 패킷 등 추가 등록 가능
    }

    /** 클라에 유틸 바인딩 상태를 전송(숫자 오버레이/바인딩 해제 즉시 반영) */
    public static void syncUtilBindings(ServerPlayer sp, PlayerEquipment eq) {
        byte[] storage = new byte[PlayerEquipment.HOTBAR_COUNT];
        int[]  index   = new int[PlayerEquipment.HOTBAR_COUNT];
        for (int i = 0; i < storage.length; i++) {
            PlayerEquipment.UtilBinding b = eq.peekBinding(i);
            if (b == null) { storage[i] = -1; index[i] = -1; }
            else {
                storage[i] = (byte)(b.storage() == PlayerEquipment.Storage.BASE ? 0 : 1);
                index[i]   = b.index();
            }
        }
        CHANNEL.sendTo(new SyncUtilBindsPacket(storage, index), sp.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }
}
