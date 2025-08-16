package fir.sec.tardoxinv.capability;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** 2D 그리드 전용 핸들러 */
public class GridItemHandler2D extends ItemStackHandler {

    private final int width;
    private final int height;

    public GridItemHandler2D(int w, int h) {
        super(Math.max(0, w) * Math.max(0, h));
        this.width = Math.max(0, w);
        this.height = Math.max(0, h);
    }

    public int getGridWidth()  { return width; }
    public int getGridHeight() { return height; }

    /** 현재 인덱스를 앵커로 두었을 때 stack이 들어갈 수 있는지 검사 */
    public boolean canPlaceAt(int anchorIndex, ItemStack stack) {
        if (anchorIndex < 0 || anchorIndex >= getSlots()) return false;
        int ax = anchorIndex % width;
        int ay = anchorIndex / width;

        int w = sizeOf(stack, "Width");
        int h = sizeOf(stack, "Height");
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;

        if (ax + w > width || ay + h > height) return false;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!getStackInSlot(idx(ax + x, ay + y)).isEmpty()) return false;
        }
        return true;
    }

    /** 슬롯이 앵커(실제 스택이 들어있는 칸)인지 */
    public boolean isAnchor(int index) {
        if (index < 0 || index >= getSlots()) return false;
        return !getStackInSlot(index).isEmpty();
    }

    /** 2D 삽입(빈 영역 자동 탐색) */
    public ItemStack insertItem2D(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int w = sizeOf(stack, "Width");
        int h = sizeOf(stack, "Height");
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;

        for (int ay = 0; ay <= height - h; ay++) {
            for (int ax = 0; ax <= width - w; ax++) {
                if (free(ax, ay, w, h)) {
                    int anchor = idx(ax, ay);
                    if (!simulate) {
                        setStackInSlot(anchor, stack.copy());
                        var tag = getStackInSlot(anchor).getOrCreateTag();
                        tag.putInt("AnchorX", ax);
                        tag.putInt("AnchorY", ay);
                        tag.putInt("SizeW", w);
                        tag.putInt("SizeH", h);
                    }
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }

    /** 앵커에서 꺼내기 */
    public ItemStack extractItem2D(int slot, int amount, boolean simulate) {
        ItemStack cur = getStackInSlot(slot);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);
        return cur;
    }

    // ===== util =====
    private int sizeOf(ItemStack st, String key) {
        var t = st.getTag();
        return (t != null && t.contains(key)) ? Math.max(0, t.getInt(key)) : 0;
    }
    private boolean free(int ax, int ay, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!getStackInSlot(idx(ax + x, ay + y)).isEmpty()) return false;
        }
        return true;
    }
    private int idx(int x, int y) { return y * width + x; }
}
