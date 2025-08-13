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
    }

    public static void syncToClient(ServerPlayer player, PlayerEquipment equipment) {
        syncBackpackToClient(player, equipment);
    }

    public static void syncBackpackToClient(ServerPlayer player, PlayerEquipment equipment) {
        CompoundTag data = new CompoundTag();

        // ★ Backpack(그리드) + BackpackItem 동시 전송
        CompoundTag bp = new CompoundTag();
        bp.putInt("Width",  equipment.getBackpackWidth());
        bp.putInt("Height", equipment.getBackpackHeight());
        bp.put("Items", equipment.getBackpack().serializeNBT());
        data.put("Backpack", bp);

        if (!equipment.getBackpackItem().isEmpty()) {
            data.put("BackpackItem", equipment.getBackpackItem().save(new CompoundTag())); // ★ 추가
        } else {
            data.put("BackpackItem", new CompoundTag()); // 빈 태그로 표시
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncEquipmentPacket(data));
    }

    public static void sendOpenEquipment(int w, int h) {
        CHANNEL.sendToServer(new OpenEquipmentPacket(w, h));
    }

    public static void syncGamerule(ServerPlayer player, boolean useCustom) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncGamerulePacket(useCustom));
    }
}
