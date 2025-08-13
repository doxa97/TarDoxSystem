package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class SyncEquipmentPacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(TarDoxInv.MODID, "sync_equipment"),
                () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals
        );

        int id = 0;
        CHANNEL.registerMessage(id++, SyncEquipmentPacket.class, SyncEquipmentPacket::encode, SyncEquipmentPacket::decode, SyncEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, OpenEquipmentPacket.class,  OpenEquipmentPacket::encode,  OpenEquipmentPacket::decode,  OpenEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, AssignFromSlotPacket.class, AssignFromSlotPacket::encode, AssignFromSlotPacket::decode, AssignFromSlotPacket::handle);
        CHANNEL.registerMessage(id++, AssignHotbarPacket.class,   AssignHotbarPacket::encode,   AssignHotbarPacket::decode,   AssignHotbarPacket::handle);
        CHANNEL.registerMessage(id++, RotateCarriedPacket.class,  RotateCarriedPacket::encode,  RotateCarriedPacket::decode,  RotateCarriedPacket::handle);
        CHANNEL.registerMessage(id++, SyncGamerulePacket.class,   SyncGamerulePacket::encode,   SyncGamerulePacket::decode,   SyncGamerulePacket::handle);
        CHANNEL.registerMessage(id++, DropBackpackPacket.class,   DropBackpackPacket::encode,   DropBackpackPacket::decode,   DropBackpackPacket::handle);
        // ★ 신규: 바인딩 오버레이 동기화
        CHANNEL.registerMessage(id++, SyncUtilBindsPacket.class,  SyncUtilBindsPacket::encode,  SyncUtilBindsPacket::decode,  SyncUtilBindsPacket::handle);
    }

    public static void syncToClient(ServerPlayer player, PlayerEquipment equipment) {
        syncBackpackToClient(player, equipment);
        // 바인딩 정보도 같이
        syncUtilBindings(player, equipment);
    }

    public static void syncBackpackToClient(ServerPlayer player, PlayerEquipment equipment) {
        CompoundTag data = new CompoundTag();
        CompoundTag bp = new CompoundTag();
        bp.putInt("Width",  equipment.getBackpackWidth());
        bp.putInt("Height", equipment.getBackpackHeight());
        bp.put("Items", equipment.getBackpack().serializeNBT());
        data.put("Backpack", bp);

        if (!equipment.getBackpackItem().isEmpty()) {
            data.put("BackpackItem", equipment.getBackpackItem().save(new CompoundTag()));
        } else {
            data.put("BackpackItem", new CompoundTag());
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncEquipmentPacket(data));
    }

    /** ★ 오버레이 표시용 바인딩 동기화 */
    public static void syncUtilBindings(ServerPlayer player, PlayerEquipment equipment) {
        SyncUtilBindsPacket.send(player, equipment);
    }

    public static void sendOpenEquipment(int w, int h) {
        CHANNEL.sendToServer(new OpenEquipmentPacket(w, h));
    }

    public static void syncGamerule(ServerPlayer player, boolean useCustom) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncGamerulePacket(useCustom));
    }
}
