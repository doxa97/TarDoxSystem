package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 배낭 드롭:
 *  - 내부 인벤토리 NBT 보존
 *  - 월드에 드롭
 *  - 바인딩/배낭 초기화 + 화면 리프레시(열려있던 화면에서 NPE 방지)
 */
public class DropBackpackPacket {

    public static void encode(DropBackpackPacket pkt, FriendlyByteBuf buf) {}
    public static DropBackpackPacket decode(FriendlyByteBuf buf) { return new DropBackpackPacket(); }

    public static void handle(DropBackpackPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
                ItemStack cur = eq.getBackpackItem();
                if (cur.isEmpty()) return;

                // 내부 직렬화
                CompoundTag data = new CompoundTag();
                data.putInt("W", eq.getBackpackWidth());
                data.putInt("H", eq.getBackpackHeight());
                data.put("Items", eq.getBackpack2D().serializeNBT());

                // 배낭 스택에 저장
                ItemStack drop = cur.copy();
                drop.getOrCreateTag().put("BackpackData", data);

                // 월드에 드롭
                ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), drop);
                sp.level().addFreshEntity(ent);

                // 정리: 유틸 바인딩 해제 + 배낭 초기화 + 장착 해제
                // new 브랜치 기준: clearAllUtilityBindings() 가 존재. 없으면 기존 이름으로 교체.
                eq.clearAllUtilityBindings();
                eq.resizeBackpack(0, 0);
                eq.setBackpackItem(ItemStack.EMPTY);

                // 화면 리프레시 & 동기화
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
                // 열려있던 화면이 배낭 그리드를 참조 중이면 NPE가 나므로, 재오픈(0x0 크기)으로 안전하게 갱신
                SyncEquipmentPacketHandler.openEquipmentScreen(sp, 0, 0);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
