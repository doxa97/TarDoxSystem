package fir.sec.tardoxinv.item;

import fir.sec.tardoxinv.TarDoxInv;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TarDoxInv.MODID);

    public static final RegistryObject<Item> SMALL_BACKPACK = ITEMS.register("small_backpack",
            () -> new BackpackItem(new Item.Properties().stacksTo(1), 2, 4));

    public static final RegistryObject<Item> MEDIUM_BACKPACK = ITEMS.register("medium_backpack",
            () -> new BackpackItem(new Item.Properties().stacksTo(1), 3, 5));

    public static final RegistryObject<Item> LARGE_BACKPACK = ITEMS.register("large_backpack",
            () -> new BackpackItem(new Item.Properties().stacksTo(1), 4, 6));
}
