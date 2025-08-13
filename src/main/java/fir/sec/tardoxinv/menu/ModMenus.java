package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.TarDoxInv;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TarDoxInv.MODID);

    public static final RegistryObject<MenuType<EquipmentMenu>> EQUIPMENT_MENU =
            MENUS.register("equipment_menu",
                    () -> IForgeMenuType.create((id, inv, buf) -> {
                        int w = buf.readVarInt();
                        int h = buf.readVarInt();
                        return new EquipmentMenu(id, inv, w, h);
                    })
            );
}
