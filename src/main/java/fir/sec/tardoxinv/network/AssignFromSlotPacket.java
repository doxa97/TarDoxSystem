package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.menu.GridSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AssignFromSlotPacket {
    private final int slotId;
    private final int hotbarIndex; // 4..8

    public AssignFromSlotPacket(int slotId, int hotbarIndex){ this.slotId=slotId; this.hotbarIndex=hotbarIndex; }
    public static void encode(AssignFromSlotPacket m, FriendlyByteBuf b){ b.writeVarInt(m.slotId); b.writeVarInt(m.hotbarIndex); }
    public static AssignFromSlotPacket decode(FriendlyByteBuf b){ return new AssignFromSlotPacket(b.readVarInt(), b.readVarInt()); }

    public static void handle(AssignFromSlotPacket m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender(); if (sp==null) return;
            boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
            if (!use) return;
            if (m.hotbarIndex < 4 || m.hotbarIndex > 8) return;
            if (m.slotId < 0 || m.slotId >= sp.containerMenu.slots.size()) return;

            Slot src = sp.containerMenu.slots.get(m.slotId);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // GridSlot이면 배낭, 아니면 기본 2x2로 처리
                if (src instanceof GridSlot gs) {
                    cap.bindFromBackpack(sp, m.hotbarIndex, gs.getGridIndex());
                } else {
                    // 메뉴에서 base 2x2 슬롯 인덱스 얻기 어려우면, 슬롯 컨테이너가 플레이어 인벤/기타이면 무시하고
                    // 커서 기반 할당을 쓰게 하거나, 별도로 base2x2 슬롯을 만드는 스크린에서만 이 패킷을 보냄.
                    // 여기서는 안전하게 무시.
                }
                SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
