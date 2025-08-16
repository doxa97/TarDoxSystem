package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncEquipmentPacket {
    private final CompoundTag data;
    public SyncEquipmentPacket(CompoundTag data) { this.data = data; }

    public static void encode(SyncEquipmentPacket msg, FriendlyByteBuf buf) { buf.writeNbt(msg.data); }
    public static SyncEquipmentPacket decode(FriendlyByteBuf buf) { return new SyncEquipmentPacket(buf.readNbt()); }

    public static void handle(SyncEquipmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                CompoundTag bp = msg.data.getCompound("Backpack");
                int w = bp.getInt("Width");
                int h = bp.getInt("Height");

                // BackpackItem 먼저 반영(아이콘 표시)
                CompoundTag it = msg.data.getCompound("BackpackItem");
                ItemStack vis = it.isEmpty() ? ItemStack.EMPTY : ItemStack.of(it);
                cap.setBackpackItem(vis.copy());

                // 크기/내용 반영
                cap.resizeBackpack(w, h);
                cap.getBackpack().deserializeNBT(bp.getCompound("Items"));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
