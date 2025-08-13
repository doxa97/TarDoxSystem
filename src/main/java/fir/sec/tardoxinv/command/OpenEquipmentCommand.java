package fir.sec.tardoxinv.command;

import com.mojang.brigadier.CommandDispatcher;
import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;

public class OpenEquipmentCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("openequipment")
                        .requires(source -> source.hasPermission(0))
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayerOrException();
                            boolean useCustom = GameRuleRegister.USE_CUSTOM_INVENTORY != null &&
                                    sp.getServer().getGameRules().getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);

                            if (!useCustom) {
                                sp.sendSystemMessage(Component.literal("기본 인벤토리를 여세요 (E 키 사용)"));
                                return 1;
                            }

                            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                                int w = cap.getBackpackWidth();
                                int h = cap.getBackpackHeight();
                                NetworkHooks.openScreen(
                                        sp,
                                        new SimpleMenuProvider(
                                                (id, inv, ply) -> new EquipmentMenu(id, inv, w, h),
                                                Component.literal("Equipment")
                                        ),
                                        buf -> { buf.writeVarInt(w); buf.writeVarInt(h); }
                                );
                            });
                            return 1;
                        })
        );
    }
}
