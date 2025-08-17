package fir.sec.tardoxinv.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.client.ui.ClientHotbarBindings;
import fir.sec.tardoxinv.menu.GridSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import fir.sec.tardoxinv.menu.EquipmentMenu;


import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InventoryOverlayRenderer {

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> scr)) return;

        GuiGraphics g = e.getGuiGraphics();
        int ox = scr.getGuiLeft();
        int oy = scr.getGuiTop();

        RenderSystem.enableBlend();

        // 0) 바탕 그리드(슬롯 경계)
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot)) continue;
            int x = ox + s.x;
            int y = oy + s.y;
            // 내부 살짝 음영
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x20000000);
            // 테두리(연한 흰색)
            g.fill(x - 1, y - 1, x + 17, y,      0x60FFFFFF);
            g.fill(x - 1, y + 16, x + 17, y + 17,0x60FFFFFF);
            g.fill(x - 1, y,      x,     y + 16,0x60FFFFFF);
            g.fill(x + 16, y,     x + 17, y + 16,0x60FFFFFF);
        }

        // 1) 항상-표시: 각 아이템의 차지 영역(앵커 기준)
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot gs)) continue;
            if (!(s.container instanceof GridItemHandler2D gh)) continue;

            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            if (!gh.isAnchor(gs.getGridIndex())) continue;

            int sw = GridItemHandler2D.stackW(st);
            int sh = GridItemHandler2D.stackH(st);

            int ax = ox + s.x;
            int ay = oy + s.y;
            int bx = ax + (sw * 18);
            int by = ay + (sh * 18);

            // 옅은 파랑 계열로 배경 강조
            g.fill(ax, ay, bx, by, 0x18007ACC);
        }

        // 2) 커서 아이템 프리뷰(배치 가능=녹색, 불가=빨강)
        ItemStack carried = scr.getMenu().getCarried();
        if (!carried.isEmpty()) {
            double mx = e.getMouseX();
            double my = e.getMouseY();

            Slot hovered = null;
            for (Slot s : scr.getMenu().slots) {
                int x = ox + s.x, y = oy + s.y;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
                    hovered = s; break;
                }
            }

            if (hovered instanceof GridSlot gs && hovered.container instanceof GridItemHandler2D gh) {
                int sw = GridItemHandler2D.stackW(carried);
                int sh = GridItemHandler2D.stackH(carried);

                int ax = ox + hovered.x;
                int ay = oy + hovered.y;
                int bx = ax + (sw * 18);
                int by = ay + (sh * 18);

                boolean can = gh.canPlaceAt(gs.getGridIndex(), carried);
                int color = can ? 0x4066FF66 : 0x40FF6666;
                g.fill(ax, ay, bx, by, color);
            }
        }

        // 3) 유틸 바인딩 숫자 오버레이(슬롯 좌상단에 작게)
        var font = Minecraft.getInstance().font;
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot gs)) continue;
            Integer num = ClientHotbarBindings.getNumberFor(gs);
            if (num == null) continue;

            int x = ox + s.x;
            int y = oy + s.y;
            String t = String.valueOf(num);
            g.drawString(font, t, x + 2, y + 1, 0xFFE6E6E6, true);
        }

        RenderSystem.disableBlend();
    }

    /** 배낭 옆 고정 툴팁: 그리드 슬롯에 마우스를 올리면 슬롯 오른쪽에 툴팁 */
    @SubscribeEvent
    public static void onRenderTooltip(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> scr)) return;
        GuiGraphics g = e.getGuiGraphics();

        double mx = e.getMouseX();
        double my = e.getMouseY();
        int ox = scr.getGuiLeft();
        int oy = scr.getGuiTop();

        Slot hovered = null;
        for (Slot s : scr.getMenu().slots) {
            int x = ox + s.x, y = oy + s.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) { hovered = s; break; }
        }
        if (!(hovered instanceof GridSlot)) return;

        ItemStack st = hovered.getItem();
        if (st.isEmpty()) return;

        List<Component> lines = scr.getTooltipFromItem(Minecraft.getInstance(), st);
        // 슬롯 오른쪽으로 20px 오프셋
        int tx = ox + hovered.x + 20;
        int ty = oy + hovered.y;
        g.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), tx, ty);
    }
    private static final int COLOR_OK   = 0x6600FF00; // 초록
    private static final int COLOR_BAD  = 0x66FF0000; // 빨강

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> sc)) return;
        if (!(sc.getMenu() instanceof EquipmentMenu menu)) return;

        // 커서에 든 아이템이 있어야만 표시
        ItemStack carried = menu.getCarried();
        if (carried == null || carried.isEmpty()) return;

        GuiGraphics gg = event.getGuiGraphics();

        // GUI 좌표 (슬롯 좌표는 상대 좌표이므로 스크린 좌상단을 더해줘야 함)
        int left = sc.getGuiLeft();
        int top  = sc.getGuiTop();

        // 블렌딩 켜기 (겹치는 오버레이가 자연스럽게 보이도록)
        RenderSystem.enableBlend();

        for (Slot s : menu.slots) {
            if (!(s instanceof GridSlot gs)) continue;

            // 앵커 칸만 하이라이트 (보조 칸은 표시하지 않음)
            // 핸들러 확인
            if (!(gs.getItemHandler() instanceof GridItemHandler2D gh)) continue;
            int anchor = gs.getGridIndex();
            if (!gh.isAnchor(anchor)) {
                // 비어있는 앵커 후보도 오버레이에 포함하려면 위 조건을 빼고 canPlaceAt만 검사하면 됨.
                // 여기서는 "앵커 칸"만 시각화한다는 기존 의도에 따라 anchor만 표시.
            }

            boolean can = gh.canPlaceAt(anchor, carried);

            int x = left + s.x;
            int y = top  + s.y;
            int w = 16, h = 16;

            gg.fill(x, y, x + w, y + h, can ? COLOR_OK : COLOR_BAD);
        }

        RenderSystem.disableBlend();
    }
}
