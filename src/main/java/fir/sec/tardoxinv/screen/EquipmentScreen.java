package fir.sec.tardoxinv.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import fir.sec.tardoxinv.TarDoxInv;
import fir.sec.tardoxinv.menu.EquipmentMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** 배경/그리드/우측 고정 툴팁 */
public class EquipmentScreen extends AbstractContainerScreen<EquipmentMenu> {

    private static final ResourceLocation BG =
            new ResourceLocation(TarDoxInv.MODID, "textures/gui/equipment_ui.png");

    public EquipmentScreen(EquipmentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = EquipmentMenu.TEX_W;
        this.imageHeight = EquipmentMenu.TEX_H;
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        gg.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gg);
        super.render(gg, mouseX, mouseY, partialTick);

        // 슬롯 경계선
        final int ox = this.leftPos, oy = this.topPos;
        for (Slot s : this.menu.slots) {
            final int sx = ox + s.x, sy = oy + s.y;
            gg.fill(sx - 1, sy - 1, sx + 17, sy,     0x80000000);
            gg.fill(sx - 1, sy + 16, sx + 17, sy+17, 0x80000000);
            gg.fill(sx - 1, sy,     sx,     sy+16,  0x80000000);
            gg.fill(sx + 16, sy,    sx + 17,sy+16,  0x80000000);
        }

        // 우측 고정 툴팁
        ItemStack hovered = (this.hoveredSlot != null && this.hoveredSlot.hasItem())
                ? this.hoveredSlot.getItem() : ItemStack.EMPTY;
        if (!hovered.isEmpty()) {
            final int panelX = this.leftPos + this.imageWidth + 6;
            final int panelY = this.topPos + 12;
            List<Component> lines = this.getTooltipFromItem(Minecraft.getInstance(), hovered);
            gg.renderTooltip(this.font, lines, Optional.empty(), panelX, panelY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics gg, int mouseX, int mouseY) {
        // 비활성(우측 고정만 사용)
    }
}
