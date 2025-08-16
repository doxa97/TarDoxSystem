package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SyncEquipmentPacketHandler {
    private SyncEquipmentPacketHandler() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("tardox:main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );

    private static int ID = 0;

    public static void register() {
        CHANNEL.registerMessage(ID++, OpenEquipmentPacket.class,
                OpenEquipmentPacket::encode, OpenEquipmentPacket::decode, OpenEquipmentPacket::handle);

        CHANNEL.registerMessage(ID++, SyncEquipmentPacket.class,
                SyncEquipmentPacket::encode, SyncEquipmentPacket::decode, SyncEquipmentPacket::handle);

        CHANNEL.registerMessage(ID++, SyncUtilBindsPacket.class,
                SyncUtilBindsPacket::encode, SyncUtilBindsPacket::decode, SyncUtilBindsPacket::handle);

        // 필요시: AssignHotbarPacket, AssignFromSlotPacket, DropBackpackPacket, Rotate* 등도 등록
    }

    /** 전체 스냅샷 동기화 (프로젝트 내 기존 호출과 호환) */
    public static void syncToClient(ServerPlayer sp, PlayerEquipment eq) {
        CHANNEL.sendTo(new SyncEquipmentPacket(eq.toTag()), sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    /** 게이머룰 동기화(호출부 시그니처만 만족하도록 no-op로 안전화) */
    public static void syncGamerule(ServerPlayer sp, boolean useCustom) {
        // 필요 시 Gamerule 전용 패킷을 추가해 전송.
        // 현재는 호출부 컴파일 안정화를 위해 no-op.
    }

    /** 클라에 “장비 화면 열기” */
    public static void openEquipmentScreen(ServerPlayer sp, int w, int h) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenEquipmentPacket(w, h));
    }

    /** 유틸 바인딩 스냅샷 전송 */
    public static void syncUtilBindings(ServerPlayer sp, PlayerEquipment eq) {
        PlayerEquipment.BindSnapshot s = eq.getBindingSnapshot();
        CHANNEL.sendTo(new SyncUtilBindsPacket(s.storageByHb, s.indexByHb),
                sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    /** (서버에서) 클라가 열도록 요청 */
    public static void sendOpenEquipment(int w, int h) {
        CHANNEL.sendToServer(new OpenEquipmentPacket(w, h));
    }
}
