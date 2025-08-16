package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 클라 → 서버 : 배낭 드롭 */
public class DropBackpackPacket {

    // 페이로드 없음
    public static void encode(DropBackpackPacket m, FriendlyByteBuf buf) {}
    public static DropBackpackPacket decode(FriendlyByteBuf buf) { return new DropBackpackPacket(); }

    public static void handle(DropBackpackPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                ItemStack cur = cap.getBackpackItem();
                if (cur.isEmpty()) return;

                // 배낭 안을 참조하던 모든 유틸 바인딩 해제
                cap.clearBindingsInsideBackpack();

                // 드롭 아이템 구성
                ItemStack out = cur.copy();
                CompoundTag data = new CompoundTag();
                data.putInt("Width",  cap.getBackpackWidth());
                data.putInt("Height", cap.getBackpackHeight());
                data.put("Items",     cap.getBackpack2D().serializeNBT());
                out.getOrCreateTag().put("BackpackData", data);

                ItemEntity ie = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 1.0, sp.getZ(), out);
                ie.setPickUpDelay(60);
                sp.level().addFreshEntity(ie);

                // 캡 초기화
                cap.setBackpackItem(ItemStack.EMPTY);
                cap.resizeBackpack(0,0);
                cap.remapBackpackBindingsAfterResize();
                sp.containerMenu.broadcastChanges();
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
