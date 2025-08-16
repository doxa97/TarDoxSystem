package fir.sec.tardoxinv.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import fir.sec.tardoxinv.menu.GridSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 인벤토리 오버레이 렌더러
 * - 실제 슬롯 좌표에 정확히 맞춘 그리드
 * - 커서/호버 아이템의 차지 영역 프리뷰(초록: 가능, 빨강: 불가)
 * - 바닐라 툴팁을 인벤토리 오른쪽 영역에 렌더
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InventoryOverlayRenderer {

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> scr)) return;
        if (!(scr.getMenu() instanceof EquipmentMenu menu)) return;

        GuiGraphics g = e.getGuiGraphics();
        int left = scr.getGuiLeft();
        int top  = scr.getGuiTop();

        // 1) 슬롯 그리드(반투명 회색)
        drawGridForGridSlots(g, scr, 0x40FFFFFF);

        // 2) 아이템 footprint 프리뷰
        drawFootprintPreview(g, scr, menu);

        // 3) 바닐라 툴팁을 오른쪽에 출력
        renderVanillaTooltipOnRight(g, scr, left, top);
    }

    private static void drawGridForGridSlots(GuiGraphics g, AbstractContainerScreen<?> scr, int argb) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (Slot s : scr.getMenu().slots) {
            if (!(s instanceof GridSlot)) continue;
            int x = scr.getGuiLeft() + s.x;
            int y = scr.getGuiTop()  + s.y;
            fill(g, x + 1, y + 1, x + 17, y + 17, argb);
        }
    }

    private static void drawFootprintPreview(GuiGraphics g, AbstractContainerScreen<?> scr, EquipmentMenu menu) {
        ItemStack carried = menu.getCarried();
        Slot hovered = scr.getSlotUnderMouse();
        if (!(hovered instanceof GridSlot gs)) return;

        ItemStack target = carried.isEmpty() ? gs.getItem() : carried;
        if (target.isEmpty()) return;

        Size sz = readSize(target);
        if (sz.w <= 1 && sz.h <= 1) return;

        int ax = scr.getGuiLeft() + gs.x;
        int ay = scr.getGuiTop()  + gs.y;

        boolean ok = canPlaceVisually(scr, gs, sz);
        int color = ok ? 0x4019FF19 : 0x40FF3B30;

        for (int dy = 0; dy < sz.h; dy++) {
            for (int dx = 0; dx < sz.w; dx++) {
                int x = ax + dx * 18;
                int y = ay + dy * 18;
                fill(g, x + 1, y + 1, x + 17, y + 17, color);
            }
        }
    }

    private static void renderVanillaTooltipOnRight(GuiGraphics g, AbstractContainerScreen<?> scr, int left, int top) {
        Slot hovered = scr.getSlotUnderMouse();
        ItemStack stack = ItemStack.EMPTY;
        if (hovered != null) stack = hovered.getItem();
        if (stack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(mc.player, flag));
        if (lines.isEmpty()) return;

        int tipX = left + 176 + 8; // 기본 컨테이너 너비 기준 우측 여백
        int tipY = top + 8;

        // 1.20.1 시그니처: (Font, List<Component>, Optional<TooltipComponent>, int, int)
        g.renderTooltip(mc.font, lines, stack.getTooltipImage(), tipX, tipY);
    }

    private static Size readSize(ItemStack st) {
        int w = 1, h = 1;
        CompoundTag tag = st.getTag();
        if (tag != null) {
            int W = tag.getInt("Width");
            int H = tag.getInt("Height");
            boolean rot = tag.getBoolean("Rot");
            if (W > 0) w = W;
            if (H > 0) h = H;
            if (rot) { int t = w; w = h; h = t; }
        }
        return new Size(w, h);
    }

    private static boolean canPlaceVisually(AbstractContainerScreen<?> scr, GridSlot anchor, Size sz) {
        List<GridSlot> gridSlots = new ArrayList<>();
        for (Slot s : scr.getMenu().slots) if (s instanceof GridSlot gs) gridSlots.add(gs);

        List<GridSlot> want = new ArrayList<>();
        for (int dy = 0; dy < sz.h; dy++) {
            for (int dx = 0; dx < sz.w; dx++) {
                int tx = anchor.x + dx * 18;
                int ty = anchor.y + dy * 18;
                GridSlot target = findSlotAt(gridSlots, tx, ty);
                if (target == null) return false;
                want.add(target);
            }
        }

        ItemStack self = anchor.getItem();
        for (GridSlot s : want) {
            ItemStack cur = s.getItem();
            if (!cur.isEmpty() && cur != self) return false;
        }
        return true;
    }

    private static GridSlot findSlotAt(List<GridSlot> list, int relX, int relY) {
        for (GridSlot gs : list) {
            if (gs.x == relX && gs.y == relY) return gs;
        }
        return null;
    }

    private static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
        g.fill(x1, y1, x2, y2, argb);
    }

    private record Size(int w, int h) {}
}
