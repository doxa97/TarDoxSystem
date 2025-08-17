package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 배낭 드롭:
 *  1) 현재 배낭 내부(Grid)를 NBT로 배낭 아이템에 저장
 *  2) 월드에 드롭
 *  3) 플레이어 쪽 바인딩/배낭 상태 정리(삭제 아님)
 *  4) 클라 동기화
 *
 * 페이로드 없는 단순 패킷(encode/decode 비어있음)
 */
public class DropBackpackPacket {

    // ---- 페이로드 없음 ----
    public static void encode(DropBackpackPacket pkt, net.minecraft.network.FriendlyByteBuf buf) { }
    public static DropBackpackPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new DropBackpackPacket(); }

    public static void handle(DropBackpackPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
                ItemStack curBp = eq.getBackpackItem(); // 착용중 배낭 아이템(겉보기)
                if (curBp.isEmpty()) return;

                // 1) 내부 인벤 직렬화
                CompoundTag data = new CompoundTag();
                data.putInt("W", eq.getBackpackWidth());
                data.putInt("H", eq.getBackpackHeight());
                data.put("Items", eq.getBackpack2D().serializeNBT()); // ← new 브랜치 메서드명

                // 2) 배낭 아이템에 NBT 저장
                ItemStack drop = curBp.copy();
                drop.getOrCreateTag().put("BackpackData", data);

                // 3) 월드에 드롭
                ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), drop);
                sp.level().addFreshEntity(ent);

                // 4) 플레이어 쪽 정리: 유틸 바인딩 해제 + 배낭 초기화 + 착용 해제
                //  - new 브랜치: clearAllUtilityBindings() 이름을 사용하는 것으로 가정
                //    (이름이 다르면 기존 clearBindingsInsideBackpack() 으로 교체)
                eq.clearAllUtilityBindings();
                eq.resizeBackpack(0, 0);
                eq.setBackpackItem(ItemStack.EMPTY);

                // 5) 동기화
                SyncEquipmentPacketHandler.syncToClient(sp, eq);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
