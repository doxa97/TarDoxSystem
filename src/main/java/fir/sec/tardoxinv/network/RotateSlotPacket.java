package fir.sec.tardoxinv.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotateSlotPacket {
    private final int slotId;
    public RotateSlotPacket(int slotId){ this.slotId = slotId; }
    public static void encode(RotateSlotPacket m, FriendlyByteBuf b){ b.writeVarInt(m.slotId); }
    public static RotateSlotPacket decode(FriendlyByteBuf b){ return new RotateSlotPacket(b.readVarInt()); }

    public static void handle(RotateSlotPacket m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            if (m.slotId < 0 || m.slotId >= sp.containerMenu.slots.size()) return;

            Slot slot = sp.containerMenu.slots.get(m.slotId);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !stack.hasTag()) return;
            if (!stack.getTag().contains("Width") || !stack.getTag().contains("Height")) return;

            int w = Math.max(1, stack.getTag().getInt("Width"));
            int h = Math.max(1, stack.getTag().getInt("Height"));
            stack.getTag().putInt("Width",  h);
            stack.getTag().putInt("Height", w);
            stack.getTag().putBoolean("Rotated", !stack.getTag().getBoolean("Rotated"));
            slot.set(stack.copy());

            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
