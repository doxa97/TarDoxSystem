package fir.sec.tardoxinv.client;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.menu.GridSlot;
import fir.sec.tardoxinv.screen.EquipmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 장비 화면 오버레이:
 *  - 바인딩 번호(5~9) 배지: 항상 아이템 아이콘 위에 보이도록 Z-깊이 올림
 *  - 멀티칸 아이템: 앵커 슬롯에 마우스오버 시, 점유 영역을 회색으로 표시 */
@Mod.EventBusSubscriber(modid = TarDoxInv.MODID, value = Dist.CLIENT)
public class InventoryOverlayRenderer {

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof EquipmentScreen scr)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int left = scr.getGuiLeft();
        int top  = scr.getGuiTop();

        // ----- 1) 멀티칸 점유 영역 회색 표시(호버 앵커만)
        Slot hover = scr.getSlotUnderMouse();
        if (hover instanceof GridSlot && !hover.getItem().isEmpty()) {
            ItemStack st = hover.getItem();
            int w = st.hasTag() ? Math.max(1, st.getTag().getInt("Width")) : 1;
            int h = st.hasTag() ? Math.max(1, st.getTag().getInt("Height")) : 1;
            // 슬롯 크기 18, 내부 16px 기준으로 약간 여백
            int baseX = left + hover.x;
            int baseY = top  + hover.y;
            g.pose().pushPose();
            g.pose().translate(0, 0, 250); // 아이콘 위에
            int color = 0x55000000; // 반투명 회색
            for (int dx=0; dx<w; dx++){
                for (int dy=0; dy<h; dy++){
                    int x = baseX + dx*18;
                    int y = baseY + dy*18;
                    g.fill(x+1, y+1, x+17, y+17, color);
                }
            }
            g.pose().popPose();
        }

        // ----- 2) 바인딩 번호 배지(가독성 개선: Z-깊이 상승)
        mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            final int baseStart = 1 + PlayerEquipment.EQUIP_SLOTS; // 8
            final int baseEnd   = baseStart + 4 - 1;               // 11
            final int bpStart   = baseStart + 4;                   // 12

            for (int si = 0; si < scr.getMenu().slots.size(); si++) {
                Slot slot = scr.getMenu().slots.get(si);

                // 2x2
                if (si >= baseStart && si <= baseEnd) {
                    int baseIdx = si - baseStart;
                    int hotbar = findHotbarFor(cap, PlayerEquipment.Storage.BASE, baseIdx);
                    if (hotbar >= 0) drawBadgeAbove(g, font, left + slot.x, top + slot.y, String.valueOf(hotbar + 1));
                    continue;
                }
                // 배낭
                int bpSlots = getBackpackSlots(scr.getMenu());
                if (bpSlots > 0 && si >= bpStart && si < bpStart + bpSlots) {
                    int bpIdx = si - bpStart;
                    int hotbar = findHotbarFor(cap, PlayerEquipment.Storage.BACKPACK, bpIdx);
                    if (hotbar >= 0) drawBadgeAbove(g, font, left + slot.x, top + slot.y, String.valueOf(hotbar + 1));
                }
            }
        });
    }

    private static int findHotbarFor(PlayerEquipment cap, PlayerEquipment.Storage storage, int index) {
        for (int hb = 4; hb <= 8; hb++) {
            PlayerEquipment.UtilBinding b = cap.peekBinding(hb);
            if (b != null && b.storage == storage && b.index == index) return hb;
        }
        return -1;
    }

    /** 아이콘 위로 끌어올려 표시 */
    private static void drawBadgeAbove(GuiGraphics g, Font font, int slotPx, int slotPy, String text) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        int x = slotPx + 1;
        int y = slotPy + 1;
        int w = font.width(text);
        g.fill(x - 2, y - 1, x + w + 2, y + 9, 0xAA000000);
        g.drawString(font, text, x, y, 0xFFFFFF, true);
        g.pose().popPose();
    }

    private static int getBackpackSlots(AbstractContainerMenu menu) {
        int total = menu.slots.size();
        int fixed = 1 + PlayerEquipment.EQUIP_SLOTS + 4;
        int bp = total - fixed;
        return Math.max(0, bp);
    }
}
