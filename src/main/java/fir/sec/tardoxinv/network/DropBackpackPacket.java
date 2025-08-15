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

                // 0) UI를 먼저 안전하게 닫는다 (그리드 크기 변경 전)
                if (sp.containerMenu != null) sp.closeContainer();

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

                // 3) 캡 상태 초기화: 아이템 제거 + 그리드 0×0로 축소
                cap.setBackpackItem(ItemStack.EMPTY);
                cap.resizeBackpack(0, 0);

                // 4) 클라 동기화 후, 0×0으로 다시 열어 빈 그리드 보이게
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
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
