package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenEquipmentPacket {
    private final int w, h; // 클라에서 보내지만, 서버에서는 무시하고 서버 측 값을 사용

    public OpenEquipmentPacket(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public static void encode(OpenEquipmentPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.w);
        buf.writeVarInt(msg.h);
    }

    public static OpenEquipmentPacket decode(FriendlyByteBuf buf) {
        return new OpenEquipmentPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(OpenEquipmentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean useCustom = GameRuleRegister.USE_CUSTOM_INVENTORY != null &&
                    player.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);
            if (!useCustom) return;

            player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                // 배낭 미장착이면 플레이어 인벤토리에서 slot_type=backpack 찾아 자동 장착
                if (cap.getBackpackWidth() == 0 && cap.getBackpackItem().isEmpty()) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack st = player.getInventory().getItem(i);
                        if (!st.isEmpty() && st.hasTag()
                                && "backpack".equals(st.getTag().getString("slot_type"))) {
                            cap.setBackpackItem(st.copy());
                            st.shrink(1);
                            break;
                        }
                    }
                }

                int bw = cap.getBackpackWidth();
                int bh = cap.getBackpackHeight();

                NetworkHooks.openScreen(
                        player,
                        new SimpleMenuProvider(
                                (id, inv, ply) -> new EquipmentMenu(id, inv, bw, bh),
                                Component.literal("Equipment")
                        ),
                        buf -> { buf.writeVarInt(bw); buf.writeVarInt(bh); }
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
