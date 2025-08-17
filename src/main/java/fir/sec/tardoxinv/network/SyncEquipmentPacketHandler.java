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

    private static final String PROTOCOL_VERSION = "2"; // 패킷 테이블 변경 시 버전업
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                ResourceLocation.fromNamespaceAndPath(TarDoxInv.MODID, "sync_equipment"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        int id = 0;
        CHANNEL.registerMessage(id++, SyncEquipmentPacket.class, SyncEquipmentPacket::encode, SyncEquipmentPacket::decode, SyncEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, OpenEquipmentPacket.class,  OpenEquipmentPacket::encode,  OpenEquipmentPacket::decode,  OpenEquipmentPacket::handle);
        CHANNEL.registerMessage(id++, AssignFromSlotPacket.class, AssignFromSlotPacket::encode, AssignFromSlotPacket::decode, AssignFromSlotPacket::handle);
        CHANNEL.registerMessage(id++, AssignHotbarPacket.class,   AssignHotbarPacket::encode,   AssignHotbarPacket::decode,   AssignHotbarPacket::handle);
        CHANNEL.registerMessage(id++, RotateCarriedPacket.class,  RotateCarriedPacket::encode,  RotateCarriedPacket::decode,  RotateCarriedPacket::handle);
        CHANNEL.registerMessage(id++, SyncGamerulePacket.class,   SyncGamerulePacket::encode,   SyncGamerulePacket::decode,   SyncGamerulePacket::handle);
        CHANNEL.registerMessage(id++, DropBackpackPacket.class,   DropBackpackPacket::encode,   DropBackpackPacket::decode,   DropBackpackPacket::handle);
        CHANNEL.registerMessage(id++, RotateSlotPacket.class,     RotateSlotPacket::encode,     RotateSlotPacket::decode,     RotateSlotPacket::handle);
    }

    /** 기존 호출 지점 호환용: 지금은 동기화할 추가 페이로드가 없으므로 no-op */
    public static void syncUtilBindings(ServerPlayer player, PlayerEquipment equipment) {
        // 필요 시 여기에 전용 패킷 추가
    }

    public static void syncToClient(ServerPlayer player, PlayerEquipment equipment) {
        CompoundTag data = new CompoundTag();
        syncBackpackToClient(player, equipment);
        data.put("Equipment", equipment.getEquipment().serializeNBT());

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncEquipmentPacket(data));
    }

    public static void syncBackpackToClient(ServerPlayer player, PlayerEquipment equipment) {
        CompoundTag data = new CompoundTag();

        // Backpack(그리드) + BackpackItem
        CompoundTag bp = new CompoundTag();
        bp.putInt("Width",  equipment.getBackpackWidth());
        bp.putInt("Height", equipment.getBackpackHeight());
        net.minecraft.nbt.CompoundTag itemsNbt = new net.minecraft.nbt.CompoundTag();
        try {
            var m2d = equipment.getClass().getMethod("getBackpack2D");
            var h2d = (net.minecraftforge.items.ItemStackHandler) m2d.invoke(equipment);
            itemsNbt = h2d.serializeNBT();
        } catch (Exception ignore) {
            try {
                var m1d = equipment.getClass().getMethod("getBackpack");
                var h1d = (net.minecraftforge.items.ItemStackHandler) m1d.invoke(equipment);
                itemsNbt = h1d.serializeNBT();
            } catch (Exception ignored) { }
        }
        bp.put("Items", itemsNbt);
        data.put("Backpack", bp);

        if (!equipment.getBackpackItem().isEmpty()) {
            data.put("BackpackItem", equipment.getBackpackItem().save(new CompoundTag()));
        } else {
            data.put("BackpackItem", new CompoundTag());
        }

        // 🔹 추가: 장비칸(equipment) 전체도 함께 전송
        try {
            var mEq = equipment.getClass().getMethod("getEquipment");
            var hEq = (net.minecraftforge.items.ItemStackHandler) mEq.invoke(equipment);
            data.put("Equipment", hEq.serializeNBT());
        } catch (Exception ignored) { }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncEquipmentPacket(data));
    }


    public static void sendOpenEquipment(int w, int h) {
        CHANNEL.sendToServer(new OpenEquipmentPacket(w, h));
    }

    public static void syncGamerule(ServerPlayer player, boolean useCustom) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncGamerulePacket(useCustom));
    }
}
