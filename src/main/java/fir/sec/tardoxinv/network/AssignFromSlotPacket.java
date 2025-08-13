package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AssignFromSlotPacket {
    private final int slotId;      // EquipmentMenu 내 슬롯 id
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
            ItemStack st = src.getItem();
            if (st.isEmpty() || !st.hasTag()) return;
            if (!"utility".equals(st.getTag().getString("slot_type"))) return;
            LinkIdUtil.ensureLinkId(st);
            src.set(st); // 원본에 link 보장

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                final int baseStart = 1 + PlayerEquipment.EQUIP_SLOTS;      // 0:배낭장착, 1..7:장비, 8..11:2x2, 12..:배낭
                final int baseEnd   = baseStart + 4 - 1;
                final int bpStart   = baseStart + 4;
                final int bpSlots   = cap.getBackpack().getSlots();
                if (m.slotId >= baseStart && m.slotId <= baseEnd) {
                    int baseIdx = m.slotId - baseStart;
                    cap.bindFromBase(sp, m.hotbarIndex, baseIdx);
                } else if (m.slotId >= bpStart && m.slotId < bpStart + bpSlots) {
                    int bpIdx = m.slotId - bpStart;
                    cap.bindFromBackpack(sp, m.hotbarIndex, bpIdx);
                }
                // 즉시 미러
                cap.tickMirrorUtilityHotbar(sp);
            });

            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
