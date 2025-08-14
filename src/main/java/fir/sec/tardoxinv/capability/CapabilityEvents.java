package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.GameRuleRegister;
import fir.sec.tardoxinv.TarDoxInv;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID)
public class CapabilityEvents {

    private static final ResourceLocation EQUIPMENT_CAP_ID =
            ResourceLocation.fromNamespaceAndPath(TarDoxInv.MODID, "equipment");

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        PlayerEquipment equipment = new PlayerEquipment();
        final LazyOptional<PlayerEquipment> opt = LazyOptional.of(() -> equipment);

        ICapabilityProvider provider = new ICapabilitySerializable<CompoundTag>() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return cap == ModCapabilities.EQUIPMENT ? opt.cast() : LazyOptional.empty();
            }
            @Override
            public CompoundTag serializeNBT() { return equipment.saveNBT(); }
            @Override
            public void deserializeNBT(CompoundTag nbt) { equipment.loadNBT(nbt); }
        };

        event.addCapability(EQUIPMENT_CAP_ID, provider);
        event.addListener(opt::invalidate);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(ModCapabilities.EQUIPMENT).ifPresent(oldCap -> {
            event.getEntity().getCapability(ModCapabilities.EQUIPMENT).ifPresent(newCap -> {
                newCap.loadNBT(oldCap.saveNBT());

                var server = event.getEntity().getServer();
                if (server == null) return;

                var rules = server.getGameRules();
                boolean use = rules.getBoolean(GameRuleRegister.USE_CUSTOM_INVENTORY);

                // useCustomInventory가 TRUE면 보존/드롭 동작을 항상 활성화
                boolean keepBindings = use || rules.getBoolean(GameRuleRegister.KEEP_UTILITY_BINDINGS_ON_DEATH);
                boolean dropBackpack = use || rules.getBoolean(GameRuleRegister.DROP_BACKPACK_ON_DEATH);
                boolean dropEquip    = use || rules.getBoolean(GameRuleRegister.DROP_EQUIPMENT_ON_DEATH);

                if (!keepBindings) newCap.clearAllUtilityBindings();
                if (dropBackpack) {
                    newCap.setBackpackItem(ItemStack.EMPTY);
                    newCap.resizeBackpack(0,0);
                }
                if (dropEquip) {
                    for (int i=0;i<PlayerEquipment.EQUIP_SLOTS;i++)
                        newCap.getEquipment().setStackInSlot(i, ItemStack.EMPTY);
                }
            });
        });
    }
}
