package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class DropBackpackPacket {

    public static void encode(DropBackpackPacket m, FriendlyByteBuf b) {}
    public static DropBackpackPacket decode(FriendlyByteBuf b) { return new DropBackpackPacket(); }

    public static void handle(DropBackpackPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                ItemStack cur = cap.getBackpackItem();
                if (cur.isEmpty()) return;

                // 1) 드롭 아이템 구성(내용물 NBT 포함)
                ItemStack out = cur.copy();
                var data = new net.minecraft.nbt.CompoundTag();
                data.putInt("Width",  cap.getBackpackWidth());
                data.putInt("Height", cap.getBackpackHeight());
                data.put("Items",     cap.getBackpack2D().serializeNBT());
                out.getOrCreateTag().put("BackpackData", data);

                // 2) 서버에 아이템 엔티티 스폰
                ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 1.0, sp.getZ(), out);
                ent.setPickUpDelay(60);
                sp.level().addFreshEntity(ent);

                // 3) 캡 상태 초기화
                cap.setBackpackItem(ItemStack.EMPTY);
                sp.containerMenu.broadcastChanges();
                SyncEquipmentPacketHandler.syncToClient(sp, cap);

                // 4) UI 안전하게 재오픈(크래시 방지: 먼저 닫고, 0x0으로 열기)
                if (sp.containerMenu != null) sp.closeContainer();
                int bw = 0, bh = 0;
                NetworkHooks.openScreen(
                        sp,
                        new SimpleMenuProvider(
                                (id, inv, ply) -> new EquipmentMenu(id, inv, bw, bh),
                                Component.literal("Equipment")
                        ),
                        buf -> { buf.writeVarInt(bw); buf.writeVarInt(bh); }
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
