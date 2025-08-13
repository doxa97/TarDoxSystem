package fir.sec.tardoxinv.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

public class ModCapabilities {
    public static final Capability<PlayerEquipment> EQUIPMENT =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static void register() {
        // 1.20.1 Forge: IStorage 불필요
    }
}
