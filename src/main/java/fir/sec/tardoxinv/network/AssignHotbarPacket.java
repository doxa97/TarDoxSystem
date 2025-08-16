package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
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
            if (carried.isEmpty()) return;

            LinkIdUtil.ensureLinkId(carried);
            UUID id = LinkIdUtil.getLinkId(carried);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // BASE(2x2)에서 먼저 검색(같은 link_id 또는 같은 아이템)
                int baseIdx = findInBase(cap.getBase2x2(), carried, id);
                if (baseIdx >= 0) {
                    cap.bindFromBase(sp, m.hotbarIndex, baseIdx);
                    SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
                    sp.containerMenu.broadcastChanges();
                    sp.inventoryMenu.broadcastChanges();
                    return;
                }

                // BACKPACK(2D)에서 검색(앵커만 노출되므로 getStackInSlot 순회로 충분)
                int bpIdx = findInBackpack(cap.getBackpack2D(), carried, id);
                if (bpIdx >= 0) {
                    cap.bindFromBackpack(sp, m.hotbarIndex, bpIdx);
                    SyncEquipmentPacketHandler.syncUtilBindings(sp, cap);
                    sp.containerMenu.broadcastChanges();
                    sp.inventoryMenu.broadcastChanges();
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static int findInBase(ItemStackHandler base, ItemStack target, UUID id) {
        for (int i = 0; i < base.getSlots(); i++) {
            ItemStack it = base.getStackInSlot(i);
            if (it.isEmpty()) continue;
            if (sameLink(it, id) || ItemStack.isSameItemSameTags(it, target)) {
                return i;
            }
        }
        return -1;
    }

    private static int findInBackpack(GridItemHandler2D bp, ItemStack target, UUID id) {
        for (int i = 0; i < bp.getSlots(); i++) {
            ItemStack it = bp.getStackInSlot(i);
            if (it.isEmpty()) continue; // 커버칸은 EMPTY 반환
            if (sameLink(it, id) || ItemStack.isSameItemSameTags(it, target)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameLink(ItemStack it, UUID id) {
        return it.hasTag() && it.getTag().hasUUID("link_id") && it.getTag().getUUID("link_id").equals(id);
    }
}
