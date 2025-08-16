package fir.sec.tardoxinv.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.client.ClientHotbarBindings;
import fir.sec.tardoxinv.menu.GridSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InventoryOverlayRenderer {

    @SubscribeEvent
    public static void onScreenPost(ScreenEvent.Render.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> scr)) return;

        GuiGraphics g = e.getGuiGraphics();
        final int ox = scr.getGuiLeft();
        final int oy = scr.getGuiTop();

        RenderSystem.enableBlend();
        for (Slot slot : scr.getMenu().slots) {
            int x = ox + slot.x, y = oy + slot.y;

            // 내부 살짝 채움
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x20000000);

            if (slot instanceof GridSlot gs) {
                Integer hb = ClientHotbarBindings.getNumberFor(gs);
                if (hb != null) {
                    String t = String.valueOf(hb + 1); // 1~
                    g.drawString(Minecraft.getInstance().font, t,
                            x + 10 - Minecraft.getInstance().font.width(t),
                            y + 1, 0xFFE6E6E6, true);
                }
            }
        }
        RenderSystem.disableBlend();
    }
}
