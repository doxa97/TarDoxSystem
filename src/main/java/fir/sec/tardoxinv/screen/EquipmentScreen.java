package fir.sec.tardoxinv.screen;

import fir.sec.tardoxinv.menu.EquipmentMenu;
import fir.sec.tardoxinv.network.DropBackpackPacket;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.lwjgl.glfw.GLFW;
import fir.sec.tardoxinv.network.SyncEquipmentPacketHandler;
import fir.sec.tardoxinv.network.AssignFromSlotPacket;
import fir.sec.tardoxinv.network.AssignHotbarPacket;


public class EquipmentScreen extends AbstractContainerScreen<AbstractContainerMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("tardox", "textures/gui/equipment_ui.png");

    public EquipmentScreen(AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, Component.empty());
        this.imageWidth = 176;
        this.imageHeight = 256;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        int ox = this.leftPos, oy = this.topPos;

        drawLabel(g, "헤드셋",         ox + 10,  oy +  2);
        drawLabel(g, "헬멧",           ox + 60,  oy +  2);
        drawLabel(g, "방탄복",         ox + 10,  oy + 38);

        drawLabel(g, "주무기1",        ox + 10,  oy +  68);
        drawLabel(g, "주무기2",        ox + 10,  oy +  94);
        drawLabel(g, "보조무기",       ox + 10,  oy + 120);
        drawLabel(g, "근접무기",       ox + 10,  oy + 146);

        drawLabel(g, "배낭(장착)",     ox + 90,  oy + 24);
        drawLabel(g, "기본 인벤(2x2)", ox + 80,  oy + 138);
        drawLabel(g, "배낭",           ox + 148, oy + 24);
    }

    private void drawLabel(GuiGraphics g, String text, int x, int y) {
        g.drawString(this.font, text, x, y, 0xFFFFFF, false);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) { }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 숫자키 기본 스왑 막기 전에 우리가 먼저 처리한다.

        // ① 유틸리티 5~9 복사
        if (keyCode >= GLFW.GLFW_KEY_5 && keyCode <= GLFW.GLFW_KEY_9) {
            int hotbarIndex = keyCode - GLFW.GLFW_KEY_1; // 4..8

            // (a) 마우스가 올려진 슬롯의 아이템이 유틸리티면 → 그 슬롯 기반 복사
            var slot = this.getSlotUnderMouse();
            if (slot != null) {
                var st = slot.getItem();
                if (!st.isEmpty() && st.hasTag() && "utility".equals(st.getTag().getString("slot_type"))) {
                    int slotId = this.menu.slots.indexOf(slot);
                    if (slotId >= 0) {
                        SyncEquipmentPacketHandler.CHANNEL
                                .sendToServer(new AssignFromSlotPacket(slotId, hotbarIndex));
                        return true; // 우리가 처리
                    }
                }
            }

            // (b) 커서(carried)에 들고 있는 게 유틸리티면 → carried 기반 복사
            var carried = this.menu.getCarried();
            if (!carried.isEmpty() && carried.hasTag()
                    && "utility".equals(carried.getTag().getString("slot_type"))) {
                SyncEquipmentPacketHandler.CHANNEL
                        .sendToServer(new AssignHotbarPacket(hotbarIndex));
                return true;
            }

            // 유틸이 아니면 그냥 막기만
            return true;
        }

        // ② 숫자키 1~9 기본 스왑 전부 차단(장비 화면에서는 전부 우리가 관리)
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) return true;

        // ③ 드롭 키로 "배낭 장착칸"만 해제(마우스로는 해제 금지)
        int dropKey = Minecraft.getInstance().options.keyDrop.getKey().getValue();
        if (keyCode == dropKey) {
            var slot = this.getSlotUnderMouse();
            // 배낭 장착칸은 메뉴에서 가장 먼저(addSlot) 추가된 슬롯 → 인덱스 0
            if (slot != null && !this.menu.slots.isEmpty() && slot == this.menu.slots.get(0)) {
                SyncEquipmentPacketHandler.CHANNEL.sendToServer(new DropBackpackPacket());
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

}
