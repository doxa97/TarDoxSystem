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

import java.util.UUID;
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
            ItemStack st = src.getItem();
            if (st.isEmpty() || !st.hasTag()) return;
            if (!"utility".equals(st.getTag().getString("slot_type"))) return;

            LinkIdUtil.ensureLinkId(st);
            src.set(st); // 소스 슬롯에도 동일 link 유지
            UUID id = LinkIdUtil.getLinkId(st);

            // 같은 link_id가 4~8에 있으면 비우기
            for (Slot s : sp.containerMenu.slots) {
                if (s.container == sp.getInventory()) {
                    int idx = s.getSlotIndex();
                    if (idx >= 4 && idx <= 8) {
                        ItemStack cur = s.getItem();
                        if (!cur.isEmpty() && cur.hasTag() && cur.getTag().hasUUID("link_id")
                                && id.equals(cur.getTag().getUUID("link_id"))) {
                            s.set(ItemStack.EMPTY);
                            sp.getInventory().setItem(idx, ItemStack.EMPTY);
                        }
                    }
                }
            }

            // 핫바 채우기
            sp.getInventory().setItem(m.hotbarIndex, st.copy());
            for (Slot s : sp.containerMenu.slots) {
                if (s.container == sp.getInventory() && s.getSlotIndex() == m.hotbarIndex) {
                    s.set(st.copy());
                    break;
                }
            }

            // 유틸 매핑/카운트 즉시 갱신
            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> cap.recordUtilityAssignment(sp, m.hotbarIndex, id));

            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }
}
