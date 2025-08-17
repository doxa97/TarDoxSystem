package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** 숫자키 1~4 → 서버로 무기 슬롯 활성화 요청 (0=주1,1=주2,2=보조,3=근접) */
public class WeaponHotkeyPacket {

    private static final String PROTO = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarDoxInv.MODID, "weapon_hotkey"),
            () -> PROTO, PROTO::equals, PROTO::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(
                id++,
                WeaponHotkeyPacket.class,
                WeaponHotkeyPacket::encode,
                WeaponHotkeyPacket::decode,
                WeaponHotkeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private final int kind; // 0..3
    public WeaponHotkeyPacket(int kind) { this.kind = kind; }

    public static void send(int kind) { CHANNEL.sendToServer(new WeaponHotkeyPacket(kind)); }

    public static void encode(WeaponHotkeyPacket pkt, FriendlyByteBuf buf) { buf.writeVarInt(pkt.kind); }
    public static WeaponHotkeyPacket decode(FriendlyByteBuf buf) { return new WeaponHotkeyPacket(buf.readVarInt()); }

    public static void handle(WeaponHotkeyPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(eq -> {
                int equipIndex = switch (pkt.kind) {
                    case 0 -> PlayerEquipment.SLOT_PRIM1;
                    case 1 -> PlayerEquipment.SLOT_PRIM2;
                    case 2 -> PlayerEquipment.SLOT_SEC;
                    case 3 -> PlayerEquipment.SLOT_MELEE;
                    default -> -1;
                };
                if (equipIndex < 0) return;

                int hotbarIdx = pkt.kind; // 0..3
                ItemStack equip = eq.getEquipment().getStackInSlot(equipIndex).copy();

                // 기존 핫바 아이템 정리
                ItemStack curHotbar = sp.getInventory().items.get(hotbarIdx);
                if (!curHotbar.isEmpty()) {
                    ItemStack back = eq.getEquipment().getStackInSlot(equipIndex);
                    if (back.isEmpty()) {
                        eq.getEquipment().setStackInSlot(equipIndex, curHotbar.copy());
                        sp.getInventory().items.set(hotbarIdx, ItemStack.EMPTY);
                    } else {
                        if (!sp.getInventory().add(curHotbar.copy()))
                            sp.drop(curHotbar.copy(), false);
                        sp.getInventory().items.set(hotbarIdx, ItemStack.EMPTY);
                    }
                }

                // 장비를 핫바로
                if (!equip.isEmpty()) {
                    eq.getEquipment().setStackInSlot(equipIndex, ItemStack.EMPTY);
                    sp.getInventory().items.set(hotbarIdx, equip.copy());
                    sp.getInventory().selected = hotbarIdx;
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
