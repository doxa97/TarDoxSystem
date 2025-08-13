package fir.sec.tardoxinv.capability;

import fir.sec.tardoxinv.TarDoxInv;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
        // 중요: 수명 종료 시 invalidate
        event.addListener(opt::invalidate);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(ModCapabilities.EQUIPMENT).ifPresent(oldCap -> {
            event.getEntity().getCapability(ModCapabilities.EQUIPMENT).ifPresent(newCap -> {
                newCap.loadNBT(oldCap.saveNBT());
            });
        });
    }
}
