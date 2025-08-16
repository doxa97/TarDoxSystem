package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RotateSlotPacket {
    private final int menuSlotId; // 클라이언트가 가리키던 메뉴 슬롯 인덱스

    public RotateSlotPacket(int menuSlotId) { this.menuSlotId = menuSlotId; }
    public static void encode(RotateSlotPacket m, FriendlyByteBuf b){ b.writeVarInt(m.menuSlotId); }
    public static RotateSlotPacket decode(FriendlyByteBuf b){ return new RotateSlotPacket(b.readVarInt()); }

    public static void handle(RotateSlotPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            AbstractContainerMenu menu = sp.containerMenu;
            if (!(menu instanceof EquipmentMenu eq)) return;

            // 배낭 그리드 슬롯인지 판별
            // EquipmentMenu 내부 인덱스 계산 로직에 맞춰 시도: 배낭 인덱스 = 메뉴 슬롯 인덱스 - firstIndex
            // 안전하게: 배낭 핸들러 크기와 동일 범위 안에서만 동작
            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                GridItemHandler2D gh = cap.getBackpack2D();
                int totalBp = gh.getSlots();

                // 메뉴에 추가된 배낭 슬롯은 연속이므로, 역으로 찾기 힘들면 그냥 모든 앵커를 스캔해 근접 처리 생략
                // 여기서는 간단히: 메뉴 슬롯의 컨테이너가 gh 인스턴스인지 확인
                if (m.menuSlotId < 0 || m.menuSlotId >= menu.slots.size()) return;

                var slot = menu.slots.get(m.menuSlotId);
                if (!(slot.container instanceof GridItemHandler2D)) return;

                int anchorIdx = slot.getSlotIndex(); // GridItemHandler2D의 인덱스
                if (anchorIdx < 0 || anchorIdx >= totalBp) return;

                ItemStack s = gh.getStackInSlot(anchorIdx);
                if (s.isEmpty()) return;

                // 회전: Width/Height 스왑 → 같은 앵커에 재배치 가능할 때만 적용
                int w = s.hasTag() ? Math.max(1, s.getTag().getInt("Width")) : 1;
                int h = s.hasTag() ? Math.max(1, s.getTag().getInt("Height")) : 1;

                ItemStack rotated = s.copy();
                rotated.getOrCreateTag().putInt("Width", h);
                rotated.getOrCreateTag().putInt("Height", w);

                // 기존 클러스터 제거 후 같은 자리에 시도 → 실패 시 원복
                ItemStack prev = gh.extractCluster(anchorIdx);
                boolean ok = gh.tryPlaceAt(anchorIdx, rotated);
                if (!ok) {
                    gh.tryPlaceAt(anchorIdx, s); // 원복
                }

                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
