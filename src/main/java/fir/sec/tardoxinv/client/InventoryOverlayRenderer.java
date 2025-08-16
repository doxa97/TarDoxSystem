package fir.sec.tardoxinv.client;

import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import fir.sec.tardoxinv.menu.GridSlot;
import fir.sec.tardoxinv.screen.EquipmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 인벤토리(장비창) 오버레이 렌더러 */
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

        // (A) 멀티칸 점유 영역: 항상 표시, 호버는 진하게
        Slot hover = scr.getSlotUnderMouse();
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot)) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;

            int w = st.hasTag() ? Math.max(1, st.getTag().getInt("Width")) : 1;
            int h = st.hasTag() ? Math.max(1, st.getTag().getInt("Height")) : 1;
            int baseX = left + s.x;
            int baseY = top  + s.y;

            int color = (s == hover) ? 0x66000000 : 0x33000000;
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            for (int dx=0; dx<w; dx++){
                for (int dy=0; dy<h; dy++){
                    int x = baseX + dx*18;
                    int y = baseY + dy*18;
                    g.fill(x+1, y+1, x+17, y+17, color);
                }
            }
            g.pose().popPose();
        }

        // (B) 커서(carried) 아이템 프리뷰: 배치 가능/불가 영역
        ItemStack carried = scr.getMenu().getCarried();
        if (!carried.isEmpty() && hover instanceof GridSlot gs) {
            int w = carried.hasTag() ? Math.max(1, carried.getTag().getInt("Width")) : 1;
            int h = carried.hasTag() ? Math.max(1, carried.getTag().getInt("Height")) : 1;
            int baseX = left + hover.x;
            int baseY = top  + hover.y;

            // GridSlot.mayPlace를 그대로 이용(앵커 비어있고 충돌X)
            boolean ok = gs.mayPlace(carried);
            int color = ok ? 0x5500FF00 : 0x55FF0000; // 가능: 초록, 불가: 빨강 (반투명)

            g.pose().pushPose();
            g.pose().translate(0, 0, 260);
            for (int dx=0; dx<w; dx++){
                for (int dy=0; dy<h; dy++){
                    int x = baseX + dx*18;
                    int y = baseY + dy*18;
                    g.fill(x+1, y+1, x+17, y+17, color);
                }
            }
            g.pose().popPose();

            // 간단 툴팁 (WxH / slot_type)
            String stype = carried.hasTag() ? carried.getTag().getString("slot_type") : "";
            String info  = w + "×" + h + (stype.isEmpty() ? "" : "  • " + stype);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            int mx = e.getMouseX();
            int my = e.getMouseY();
            int wtxt = font.width(info);
            g.fill(mx - 3, my - 12, mx + wtxt + 3, my, 0xC0000000);
            g.drawString(font, info, mx, my - 10, 0xFFFFFF, false);
            g.pose().popPose();
        }

        // (C) 바인딩 번호 배지 (5~9): 가독성 ↑
        mc.player.getCapability(ModCapabilities.EQUIPMENT).ifPresent(cap -> {
            final int baseStart = 1 + PlayerEquipment.EQUIP_SLOTS; // 8
            final int baseEnd   = baseStart + 4 - 1;               // 11
            final int bpStart   = baseStart + 4;                   // 12

            for (int si = 0; si < scr.getMenu().slots.size(); si++) {
                Slot slot = scr.getMenu().slots.get(si);

                // 기본 2×2
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

    private static void drawBadgeAbove(GuiGraphics g, Font font, int slotPx, int slotPy, String text) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        int x = slotPx + 1;
        int y = slotPy + 1;
        int w = font.width(text);
        g.fill(x - 3, y - 2, x + w + 3, y + 10, 0xC0000000);
        g.hLine(x - 3, x + w + 3, y - 2, 0x80FFFFFF);
        g.hLine(x - 3, x + w + 3, y + 10, 0x80000000);
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
