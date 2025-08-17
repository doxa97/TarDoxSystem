package fir.sec.tardoxinv;

import com.mojang.logging.LogUtils;
import fir.sec.tardoxinv.item.ModItems;
import fir.sec.tardoxinv.menu.ModMenus;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.server.ServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TarDoxInv.MODID)
public class TarDoxInv {
    public static final String MODID = "tardox";

    // ✅ SLF4J 로거로 통일
    public static final Logger LOGGER = LogUtils.getLogger();

    public TarDoxInv() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ServerEvents()); // 로그인시 gamerule sync

        GameRuleRegister.register();
        SyncEquipmentPacketHandler.register();
    }

}
