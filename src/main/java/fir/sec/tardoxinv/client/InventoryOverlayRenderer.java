package fir.sec.tardoxinv.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.client.ui.ClientHotbarBindings;
import fir.sec.tardoxinv.menu.slot.EquipSlot;
import fir.sec.tardoxinv.menu.slot.GridSlot;
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

import java.util.*;

/**
 * - 모든 GridSlot 위에 그리드/항상-표시/프리뷰를 그림
 * - EquipSlot(장착칸)도 1x1 그리드로 표시
 * - 바닐라 툴팁을 슬롯 오른쪽에 고정 렌더
 * - 유틸 바인딩 숫자 오버레이
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InventoryOverlayRenderer {

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> scr)) return;

        GuiGraphics g = e.getGuiGraphics();
        int ox = scr.getGuiLeft();
        int oy = scr.getGuiTop();

        RenderSystem.enableBlend();

        // 0) 모든 GridSlot/EquipSlot 외곽선(밝은 흰색) + 내부 옅은 섀도우
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot) && !(s instanceof EquipSlot)) continue;
            int x = ox + s.x;
            int y = oy + s.y;
            // 내부 살짝 음영
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x20000000);
            // 테두리
            g.fill(x - 1, y - 1, x + 17, y,       0x60FFFFFF);
            g.fill(x - 1, y + 16, x + 17, y + 17, 0x60FFFFFF);
            g.fill(x - 1, y,       x,     y + 16, 0x60FFFFFF);
            g.fill(x + 16, y,      x + 17, y + 16,0x60FFFFFF);
        }

        // 1) 항상-표시: GridSlot 내 아이템 차지영역
        //    - 같은 link_id는 한 번만 그림
        //    - link_id 없으면 해당 슬롯을 앵커로 취급
        Set<String> drawn = new HashSet<>();
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot gs)) continue;
            if (!(s.container instanceof GridItemHandler2D gh)) continue;

            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;

            String key = keyFor(st, gs.getGridIndex());
            if (!drawn.add(key)) continue; // 이미 그림

            int sw = GridItemHandler2D.stackW(st);
            int sh = GridItemHandler2D.stackH(st);

            int ax = ox + s.x;
            int ay = oy + s.y;
            int bx = ax + (sw * 18);
            int by = ay + (sh * 18);

            // 옅은 파랑 계열
            g.fill(ax, ay, bx, by, 0x18007ACC);
        }

        // 2) 커서-프리뷰(배치 가능/불가)
        ItemStack carried = scr.getMenu().getCarried();
        if (!carried.isEmpty()) {
            double mx = e.getMouseX();
            double my = e.getMouseY();

            Slot hovered = null;
            for (Slot s : scr.getMenu().slots) {
                int x = ox + s.x, y = oy + s.y;
                if (mx >= x && mx < x + 16 && my >= y && my < y + 16) { hovered = s; break; }
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

        // 3) 유틸 바인딩 숫자
        var font = Minecraft.getInstance().font;
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot gs)) continue;
            Integer num = ClientHotbarBindings.getNumberFor(gs);
            if (num == null) continue;
            int x = ox + s.x, y = oy + s.y;
            g.drawString(font, String.valueOf(num), x + 2, y + 1, 0xFFE6E6E6, true);
        }

        RenderSystem.disableBlend();
    }

    /** 바닐라 툴팁을 슬롯 오른쪽에 고정 렌더 (GridSlot/EquipSlot 대상) */
    @SubscribeEvent
    public static void onRenderTooltip(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> scr)) return;
        GuiGraphics g = e.getGuiGraphics();
        double mx = e.getMouseX(), my = e.getMouseY();
        int ox = scr.getGuiLeft(), oy = scr.getGuiTop();

        Slot hovered = null;
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot) && !(s instanceof EquipSlot)) continue;
            int x = ox + s.x, y = oy + s.y;
            if (mx >= x && mx < x + 16 && my >= y && my < y + 16) { hovered = s; break; }
        }
        if (hovered == null) return;

        ItemStack st = hovered.getItem();
        if (st.isEmpty()) return;

        List<Component> lines = scr.getTooltipFromItem(Minecraft.getInstance(), st);
        int tx = ox + hovered.x + 20;
        int ty = oy + hovered.y;
        g.renderTooltip(Minecraft.getInstance().font, lines, Optional.empty(), tx, ty);
    }

    private static String keyFor(ItemStack st, int idx) {
        var t = st.getTag();
        if (t != null && t.contains("link_id")) return "link:" + t.getString("link_id");
        return "idx:" + idx; // link_id 없으면 슬롯 인덱스를 키로
    }
}
