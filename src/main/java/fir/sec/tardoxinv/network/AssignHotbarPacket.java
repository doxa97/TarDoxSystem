package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.util.LinkIdUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
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

            ItemStack carried = sp.containerMenu.getCarried();
            if (carried.isEmpty() || !carried.hasTag()) return;
            if (!"utility".equals(carried.getTag().getString("slot_type"))) return;

            LinkIdUtil.ensureLinkId(carried);
            UUID id = LinkIdUtil.getLinkId(carried);

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // 1) link_id 또는 동일 아이템으로 원본 슬롯 찾기 (BASE → BACKPACK 순)
                int baseIdx = findByLinkOrItem(cap.getBase2x2(), carried, id);
                if (baseIdx >= 0) {
                    cap.bindFromBase(sp, m.hotbarIndex, baseIdx);
                } else {
                    int bpIdx = findByLinkOrItem(cap.getBackpack2D(), carried, id);
                    if (bpIdx >= 0) {
                        cap.bindFromBackpack(sp, m.hotbarIndex, bpIdx);
                    } else {
                        // 원본슬롯을 찾지 못하면 바인딩하지 않음 (중복 생성 방지)
                        return;
                    }
                }
                sp.containerMenu.broadcastChanges();
                sp.inventoryMenu.broadcastChanges();
            });
        });
        ctx.get().setPacketHandled(true);
    }

    /** GridItemHandler2D에서, link_id가 같거나 동일 아이템/태그인 앵커 슬롯 인덱스를 찾는다. 없으면 -1 */
    private static int findByLinkOrItem(GridItemHandler2D grid, ItemStack target, UUID id) {
        for (int i = 0; i < grid.getSlots(); i++) {
            ItemStack it = grid.getStackInSlot(i);
            if (it.isEmpty()) continue;
            if (id != null && it.hasTag() && it.getTag().hasUUID("link_id") &&
                    id.equals(it.getTag().getUUID("link_id"))) {
                return i;
            }
            if (ItemStack.isSameItemSameTags(it, target)) {
                return i;
            }
        }
        return -1;
    }
}
