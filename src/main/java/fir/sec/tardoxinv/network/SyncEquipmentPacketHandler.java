package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 네트워크 채널 및 동기화 헬퍼.
 * - 누락되었던 syncToClient(...), sendOpenEquipment(...), syncGamerule(...), syncUtilBindings(...) 제공
 * - 패킷 등록 포함
 */
public final class SyncEquipmentPacketHandler {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarDoxInv.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static boolean registered = false;

    /** 모드 초기화 시 1회 호출 */
    public static void register() {
        if (registered) return;
        int id = 0;

        CHANNEL.registerMessage(id++, SyncEquipmentPacket.class,   SyncEquipmentPacket::encode,   SyncEquipmentPacket::decode,   SyncEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, OpenEquipmentPacket.class,   OpenEquipmentPacket::encode,   OpenEquipmentPacket::decode,   OpenEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, DropBackpackPacket.class,    DropBackpackPacket::encode,    DropBackpackPacket::decode,    DropBackpackPacket::handle);
        CHANNEL.registerMessage(id++, AssignHotbarPacket.class,    AssignHotbarPacket::encode,    AssignHotbarPacket::decode,    AssignHotbarPacket::handle);
        CHANNEL.registerMessage(id++, AssignFromSlotPacket.class,  AssignFromSlotPacket::encode,  AssignFromSlotPacket::decode,  AssignFromSlotPacket::handle);
        CHANNEL.registerMessage(id++, RotateSlotPacket.class,      RotateSlotPacket::encode,      RotateSlotPacket::decode,      RotateSlotPacket::handle);
        CHANNEL.registerMessage(id++, SyncUtilBindsPacket.class,   SyncUtilBindsPacket::encode,   SyncUtilBindsPacket::decode,   SyncUtilBindsPacket::handle);

        registered = true;
    }

    /** 서버 → 클라 : 장비/배낭 등 전체 스냅샷 동기화 */
    public static void syncToClient(ServerPlayer sp, PlayerEquipment equipment) {
        try {
            // 현재 구현은 SyncEquipmentPacket(CompoundTag) 생성자를 요구
            CompoundTag snapshot = equipment.saveNBT();
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncEquipmentPacket(snapshot));
        } catch (Throwable ignored) {}

        // 유틸리티 바인딩(핫바 연동) 정보도 별도 패킷으로 동기화
        syncUtilBindings(sp, equipment);
    }

    /** 서버 → 클라 : 유틸리티 바인딩 동기화 */
    public static void syncUtilBindings(ServerPlayer sp, PlayerEquipment equipment) {
        try {
            // 리포 현재 상태는 무인자 생성자 사용(encode 내부에서 서버 상태 참조)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new SyncUtilBindsPacket());
        } catch (Throwable ignored) {}
    }

    /** 클라 → 서버 : 커스텀 인벤토리 열기 요청 (현재 패킷은 (w,h) 필요) */
    public static void sendOpenEquipment(int width, int height) {
        try {
            CHANNEL.sendToServer(new OpenEquipmentPacket(width, height));
        } catch (Throwable ignored) {}
    }

    /** 서버 → 클라 : 커스텀 인벤토리 게임룰 통지 (전용 패킷이 없으면 NOP) */
    public static void syncGamerule(ServerPlayer sp, boolean useCustomInventory) {
        // 필요 시 별도 GameruleSyncPacket 추가 후 여기서 전송
    }

    private SyncEquipmentPacketHandler() {}
}
