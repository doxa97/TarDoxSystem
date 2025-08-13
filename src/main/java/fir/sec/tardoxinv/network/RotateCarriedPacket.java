package fir.sec.tardoxinv.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotateCarriedPacket {

    public static void encode(RotateCarriedPacket msg, FriendlyByteBuf buf) {}
    public static RotateCarriedPacket decode(FriendlyByteBuf buf) { return new RotateCarriedPacket(); }

    public static void handle(RotateCarriedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack = player.containerMenu.getCarried();
            if (stack.isEmpty()) return;

            CompoundTag tag = stack.getOrCreateTag();
            if (!tag.contains("Width") || !tag.contains("Height")) return;

            int w = tag.getInt("Width");
            int h = tag.getInt("Height");
            tag.putInt("Width",  h);
            tag.putInt("Height", w);
            tag.putBoolean("Rotated", !tag.getBoolean("Rotated"));

            // 즉시 반영
            player.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
