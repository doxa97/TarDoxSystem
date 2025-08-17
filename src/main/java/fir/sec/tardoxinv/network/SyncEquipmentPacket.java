package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncEquipmentPacket {
    public final CompoundTag data;

    public SyncEquipmentPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncEquipmentPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static SyncEquipmentPacket decode(FriendlyByteBuf buf) {
        CompoundTag t = buf.readAnySizeNbt();
        return new SyncEquipmentPacket(t == null ? new CompoundTag() : t);
    }

    public static void handle(SyncEquipmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null) return;

            player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // ① Equipment 먼저 반영 (핫바 미러/오버레이 로직이 이 값에 의존)
                if (msg.data.contains("Equipment")) {
                    cap.getEquipment().deserializeNBT(msg.data.getCompound("Equipment"));
                }

                // ② Base2x2 적용
                if (msg.data.contains("Base2x2")) {
                    cap.getBase2x2().deserializeNBT(msg.data.getCompound("Base2x2"));
                }

                // ③ BackpackItem
                if (msg.data.contains("BackpackItem")) {
                    var tag = msg.data.getCompound("BackpackItem");
                    // 빈 태그면 EMPTY로
                    net.minecraft.world.item.ItemStack vis = tag.isEmpty()
                            ? net.minecraft.world.item.ItemStack.EMPTY
                            : net.minecraft.world.item.ItemStack.of(tag);
                    cap.setBackpackItem(vis.copy());
                }

                // ④ Backpack (크기 → 내용물 순서 중요)
                if (msg.data.contains("Backpack")) {
                    CompoundTag bp = msg.data.getCompound("Backpack");
                    int w = bp.getInt("Width");
                    int h = bp.getInt("Height");

                    // 리사이즈가 내용물 적용보다 먼저!
                    cap.resizeBackpack(w, h);

                    if (bp.contains("Items")) {
                        cap.getBackpack2D().deserializeNBT(bp.getCompound("Items"));
                    }
                }

                // (선택) UI/오버레이 갱신 훅이 있다면 여기서 호출
                // fir.sec.tardoxinv.client.ui.ClientHotbarBindings.onSync(cap);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
