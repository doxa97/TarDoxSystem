package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class AssignHotbarPacket {
    private final int hotbarIndex; // 4..8
    public AssignHotbarPacket(int hotbarIndex){ this.hotbarIndex = hotbarIndex; }

    public static void encode(AssignHotbarPacket m, FriendlyByteBuf b){ b.writeVarInt(m.hotbarIndex); }
    public static AssignHotbarPacket decode(FriendlyByteBuf b){ return new AssignHotbarPacket(b.readVarInt()); }

    public static void handle(AssignHotbarPacket m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender(); if (sp==null) return;
            boolean use = sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
            if (!use) return;
            if (m.hotbarIndex < 4 || m.hotbarIndex > 8) return;

            ItemStack carried = sp.containerMenu.getCarried();
            if (carried.isEmpty() || !carried.hasTag()) return;
            if (!"utility".equals(carried.getTag().getString("slot_type"))) return;

            LinkIdUtil.ensureLinkId(carried);
            UUID id = LinkIdUtil.getLinkId(carried);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // base 먼저, 없으면 backpack에서 같은 link/item 탐색
                int baseIdx = findInHandler(cap.getBase2x2(), carried, id);
                if (baseIdx >= 0) {
                    cap.bindFromBase(sp, m.hotbarIndex, baseIdx);
                    SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
                    return;
                }
                int bpIdx = findInHandler(cap.getBackpack(), carried, id);
                if (bpIdx >= 0) {
                    cap.bindFromBackpack(sp, m.hotbarIndex, bpIdx);
                    SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static int findInHandler(ItemStackHandler h, ItemStack exemplar, UUID id) {
        if (h == null) return -1;
        for (int i=0;i<h.getSlots();i++) {
            ItemStack it = h.getStackInSlot(i);
            if (it.isEmpty()) continue;
            if (id != null && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) return i;
            if (it.getItem()==exemplar.getItem()) return i;
        }
        return -1;
    }
}
