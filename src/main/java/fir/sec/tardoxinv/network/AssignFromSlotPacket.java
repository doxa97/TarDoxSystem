package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.menu.GridSlot;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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

            Slot srcSlot = sp.containerMenu.slots.get(m.slotId);
            ItemStack st = srcSlot.getItem();
            if (st.isEmpty() || !st.hasTag()) return;
            if (!"utility".equals(st.getTag().getString("slot_type"))) return;
            LinkIdUtil.ensureLinkId(st);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                if (srcSlot instanceof GridSlot gs) {
                    if (gs.getStorage()== GridSlot.Storage.BASE)
                        cap.bindFromBase(sp, m.hotbarIndex, gs.getGridIndex());
                    else
                        cap.bindFromBackpack(sp, m.hotbarIndex, gs.getGridIndex());
                } else {
                    // 그리드 슬롯이 아니면(예외) 바닐라 인벤으로부터는 바인딩 안함
                    return;
                }
                // 클라 HUD용 바인딩 동기화
                SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
