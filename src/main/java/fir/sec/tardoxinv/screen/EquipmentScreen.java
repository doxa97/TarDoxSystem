package fir.sec.tardoxinv.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * 배경 이미지를 그리지 않는다(“Equipment/보관함” 글씨 제거).
 * 슬롯 경계선만 최소 렌더.
 * 툴팁은 우측 고정이 아니라 바닐라 위치(필요시 다시 고정 버전으로 바꿀 수 있음).
 */
public class EquipmentScreen extends AbstractContainerScreen<EquipmentMenu> {

    public EquipmentScreen(EquipmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = EquipmentMenu.TEX_W;
        this.imageHeight = EquipmentMenu.TEX_H;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        // 배경 렌더 없음 → 기존 텍스트 노출 제거
        // 패널 느낌을 원하면 아래 fill 주석 해제
        // gg.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg);
        super.render(gg, mouseX, mouseY, partialTick);

        // 슬롯 경계선(얇게)
        RenderSystem.enableBlend();
        final int ox = this.leftPos, oy = this.topPos;
        for (Slot s : this.menu.slots) {
            final int x = ox + s.x, y = oy + s.y;
            gg.fill(x - 1, y - 1, x + 17, y,     0x60000000);
            gg.fill(x - 1, y + 16, x + 17, y+17, 0x60000000);
            gg.fill(x - 1, y,     x,     y+16,  0x60000000);
            gg.fill(x + 16, y,    x + 17, y+16, 0x60000000);
        }
        RenderSystem.disableBlend();

        // 바닐라 방식 툴팁(우측 고정 X)
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack hovered = this.hoveredSlot.getItem();
            List<Component> lines = this.getTooltipFromItem(Minecraft.getInstance(), hovered);
            gg.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
        }

        // 기본 포어그라운드 텍스트는 안 씀
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        // 타이틀/“보관함” 같은 텍스트 비활성화
    }
}
