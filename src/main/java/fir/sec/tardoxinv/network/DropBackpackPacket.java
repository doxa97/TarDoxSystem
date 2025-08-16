package fir.sec.tardoxinv.network;

import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DropBackpackPacket {

    public static void encode(DropBackpackPacket m, FriendlyByteBuf b) {}
    public static DropBackpackPacket decode(FriendlyByteBuf b) { return new DropBackpackPacket(); }

    public static void handle(DropBackpackPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            sp.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
                ItemStack cur = cap.getBackpackItem();
                if (cur.isEmpty()) return;

                // 0) 현재 배낭 내부의 link_id 수집(이후 핫바 5~9 해제에 사용)
                Set<java.util.UUID> backpackIds = new HashSet<>();
                try {
                    var getBp = cap.getClass().getMethod("getBackpack2D");
                    var bp = (net.minecraftforge.items.ItemStackHandler) getBp.invoke(cap);
                    for (int i = 0; i < bp.getSlots(); i++) {
                        ItemStack it = bp.getStackInSlot(i);
                        if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")) {
                            backpackIds.add(it.getTag().getUUID("link_id"));
                        }
                    }
                } catch (Exception ignore) {
                    try {
                        var getBp = cap.getClass().getMethod("getBackpack");
                        var bp = (net.minecraftforge.items.ItemStackHandler) getBp.invoke(cap);
                        for (int i = 0; i < bp.getSlots(); i++) {
                            ItemStack it = bp.getStackInSlot(i);
                            if (!it.isEmpty() && it.hasTag() && it.getTag().hasUUID("link_id")) {
                                backpackIds.add(it.getTag().getUUID("link_id"));
                            }
                        }
                    } catch (Exception ignored) { }
                }

                // 1) UI 먼저 닫기(안전)
                if (sp.containerMenu != null) sp.closeContainer();

                // 2) 드롭 아이템(NBT 포함)
                ItemStack out = cur.copy();
                var data = new net.minecraft.nbt.CompoundTag();
                data.putInt("Width",  cap.getBackpackWidth());
                data.putInt("Height", cap.getBackpackHeight());
                // getBackpack2D 우선
                net.minecraft.nbt.CompoundTag itemsNbt = new net.minecraft.nbt.CompoundTag();
                try {
                    var getBp = cap.getClass().getMethod("getBackpack2D");
                    var bp = (net.minecraftforge.items.ItemStackHandler) getBp.invoke(cap);
                    itemsNbt = bp.serializeNBT();
                } catch (Exception ignore) {
                    try {
                        var getBp = cap.getClass().getMethod("getBackpack");
                        var bp = (net.minecraftforge.items.ItemStackHandler) getBp.invoke(cap);
                        itemsNbt = bp.serializeNBT();
                    } catch (Exception ignored) { }
                }
                data.put("Items", itemsNbt);
                out.getOrCreateTag().put("BackpackData", data);

                ItemEntity ent = new ItemEntity(sp.level(), sp.getX(), sp.getY() + 1.0, sp.getZ(), out);
                ent.setPickUpDelay(60);
                sp.level().addFreshEntity(ent);

                // 3) 캡 상태 초기화
                cap.setBackpackItem(ItemStack.EMPTY);
                cap.resizeBackpack(0, 0);

                // 4) 배낭 유틸 link_id와 매치되는 핫바(5~9) 즉시 비움
                for (int i = 4; i <= 8; i++) {
                    ItemStack hb = sp.getInventory().getItem(i);
                    if (hb.isEmpty() || !hb.hasTag() || !hb.getTag().hasUUID("link_id")) continue;
                    java.util.UUID id = hb.getTag().getUUID("link_id");
                    if (backpackIds.contains(id)) {
                        sp.getInventory().setItem(i, ItemStack.EMPTY);
                        // 컨테이너 슬롯도 동기화
                        if (sp.inventoryMenu != null) {
                            sp.inventoryMenu.broadcastChanges();
                        }
                    }
                }

                // 5) 클라 동기화 + 0x0로 재오픈
                SyncEquipmentPacketHandler.syncToClient(sp, cap);
                int bw = 0, bh = 0;
                NetworkHooks.openScreen(
                        sp,
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
