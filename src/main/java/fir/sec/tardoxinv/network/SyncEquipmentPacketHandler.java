package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * 채널/패킷 등록 + 동기화 헬퍼(프로젝트 내 기존 호출부 모두 만족)
 */
public final class SyncEquipmentPacketHandler {
    private SyncEquipmentPacketHandler() {}

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarDoxInv.MODID, "main"),
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

        // 필요시: AssignHotbarPacket, AssignFromSlotPacket, DropBackpackPacket, Rotate* 등도 여기서 등록
    }

    // ── 기존 호출부와 호환되는 헬퍼들 ──
    public static void syncToClient(ServerPlayer sp, PlayerEquipment eq) {
        CHANNEL.sendTo(new SyncEquipmentPacket(eq.toTag()), sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void syncGamerule(ServerPlayer sp, boolean useCustom) {
        // (옵션) 게이머룰 동기화 패킷 별도로 있다면 등록/전송
        // 일단 전체 스냅샷으로 대체 가능
        syncToClient(sp, sp.getCapability(/* 너의 Cap 키 */null).orElse(eqFallback()));
    }

    private static PlayerEquipment eqFallback() { return new PlayerEquipment(); }

    public static void sendOpenEquipment(int w, int h) {
        // 클라→서버: 열어달라
        CHANNEL.sendToServer(new OpenEquipmentPacket(w, h));
    }

    public static void openEquipmentScreen(ServerPlayer sp, int w, int h) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new OpenEquipmentPacket(w, h));
    }

    public static void syncUtilBindings(ServerPlayer sp, PlayerEquipment eq) {
        PlayerEquipment.BindSnapshot s = eq.getBindingSnapshot();
        CHANNEL.sendTo(new SyncUtilBindsPacket(s.storageByHb, s.indexByHb),
                sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
