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

            ItemStack st = sp.containerMenu.getCarried();
            if (st.isEmpty() || !st.hasTag()) return;
            if (!"utility".equals(st.getTag().getString("slot_type"))) return;

            LinkIdUtil.ensureLinkId(st);
            UUID id = LinkIdUtil.getLinkId(st);

            // 저장소에도 같은 아이템 있으면 link 맞춰주기(최초 할당 보정)
            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                var base = cap.getBase2x2();
                var bp   = cap.getBackpack();
                java.util.function.Consumer<net.minecraftforge.items.ItemStackHandler> fix = handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack it = handler.getStackInSlot(i);
                        if (it.isEmpty()) continue;
                        if (!it.hasTag() || !it.getTag().hasUUID("link_id")) {
                            if (it.getItem() == st.getItem()) {
                                it.getOrCreateTag().putUUID("link_id", id);
                                handler.setStackInSlot(i, it);
                                break;
                            }
                        }
                    }
                };
                fix.accept(base);
                fix.accept(bp);
            });

            // 같은 link_id가 4~8에 있으면 비움
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
