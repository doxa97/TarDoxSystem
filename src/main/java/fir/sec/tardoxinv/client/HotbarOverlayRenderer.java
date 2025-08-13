package fir.sec.tardoxinv.client;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, value = Dist.CLIENT)
public class HotbarOverlayRenderer {

    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiOverlayEvent.Post e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 스크린 좌표 계산
        int sw = e.getWindow().getGuiScaledWidth();
        int sh = e.getWindow().getGuiScaledHeight();
        int left = (sw - 182) / 2;
        int top  = sh - 23;

        mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            GuiGraphics g = e.getGuiGraphics();
            var font = mc.font;

            for (int hb = 4; hb <= 8; hb++) {
                PlayerEquipment.UtilBinding b = cap.peekBinding(hb);
                if (b == null) continue;

                String label = b.storage == PlayerEquipment.Storage.BASE
                        ? ("P" + (b.index + 1))     // Pocket(2x2)
                        : ("B" + (b.index + 1));    // Backpack grid (1-based)

                int slotX = left + 3 + hb * 20;   // 슬롯 아이콘의 좌상단 근처
                int slotY = top - 9;              // 핫바 위쪽 약간 윗부분

                int w = font.width(label);
                // 반투명 배경
                g.fill(slotX - 2, slotY - 1, slotX + w + 2, slotY + 9, 0x88000000);
                // 텍스트(그림자)
                g.drawString(font, label, slotX, slotY, 0xFFFFFF, true);
            }
        });
    }
}
