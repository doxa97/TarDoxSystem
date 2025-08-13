package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

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
            var id = LinkIdUtil.getLinkId(carried);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // 우선 link_id로 원본 슬롯 탐색 → 없으면 동일 아이템 첫 슬롯
                int baseIdx = findByLinkOrItem(cap.getBase2x2(), carried, id);
                if (baseIdx >= 0) {
                    cap.bindFromBase(sp, m.hotbarIndex, baseIdx);
                } else {
                    int bpIdx = findByLinkOrItem(cap.getBackpack(), carried, id);
                    if (bpIdx >= 0) {
                        cap.bindFromBackpack(sp, m.hotbarIndex, bpIdx);
                    } else {
                        return; // 원본이 없으면 바인딩 불가
                    }
                }
                cap.tickMirrorUtilityHotbar(sp);
            });

            sp.containerMenu.broadcastChanges();
            sp.inventoryMenu.broadcastChanges();
        });
        ctx.get().setPacketHandled(true);
    }

    private static int findByLinkOrItem(net.minecraftforge.items.ItemStackHandler h, ItemStack target, java.util.UUID id) {
        // 1) link_id 일치 우선
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack it = h.getStackInSlot(i);
            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")
                    && id.equals(it.getTag().getUUID("link_id"))) return i;
        }
        // 2) 같은 아이템(태그 포함) 첫 슬롯
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack it = h.getStackInSlot(i);
            if (!it.isEmpty() && ItemStack.isSameItemSameTags(it, target)) return i;
        }
        return -1;
    }
}
