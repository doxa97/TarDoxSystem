package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.core.NonNullList;

public class GridItemHandler2D extends ItemStackHandler {
    private final int width;
    private final int height;
    private int[] cover; // -1=empty, anchorIndex otherwise

    public GridItemHandler2D(int width, int height) {
        super(Math.max(0, width * height));
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.stacks = NonNullList.withSize(getSlots(), ItemStack.EMPTY);
        this.cover = new int[getSlots()];
        java.util.Arrays.fill(this.cover, -1);
    }

    public int getGridWidth() { return width; }
    public int getGridHeight() { return height; }

    private int idx(int x, int y) { return y * width + x; }
    private int ix(int index) { return index % width; }
    private int iy(int index) { return index / width; }

    private static int wOf(ItemStack s) { return s.hasTag() ? Math.max(1, s.getTag().getInt("Width")) : 1; }
    private static int hOf(ItemStack s) { return s.hasTag() ? Math.max(1, s.getTag().getInt("Height")) : 1; }

    /** 공개: 오버레이/슬롯에서 앵커 여부를 확인 */
    public boolean isAnchor(int index) {
        return index >= 0 && index < getSlots() && cover[index] == index && !stacks.get(index).isEmpty();
    }

    private boolean fitsRectAt(int anchorIndex, int w, int h) {
        if (w <= 0 || h <= 0) return false;
        int ax = ix(anchorIndex), ay = iy(anchorIndex);
        if (ax + w > width || ay + h > height) return false;
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int i = idx(ax + dx, ay + dy);
                if (cover[i] != -1) return false;
                if (!stacks.get(i).isEmpty()) return false;
            }
        }
        return true;
    }

    /** 공개: 특정 아이템을 해당 앵커 자리에 둘 수 있는지(격자 미리보기/슬롯 검증용) */
    public boolean canPlaceAt(int anchorIndex, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return fitsRectAt(anchorIndex, wOf(stack), hOf(stack));
    }

    private void markArea(int anchorIndex, int w, int h, boolean occupy) {
        int ax = ix(anchorIndex), ay = iy(anchorIndex);
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int i = idx(ax + dx, ay + dy);
                cover[i] = occupy ? anchorIndex : -1;
                if (i != anchorIndex && occupy) {
                    if (!stacks.get(i).isEmpty()) stacks.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    public ItemStack extractCluster(int anyIndex) {
        if (anyIndex < 0 || anyIndex >= getSlots()) return ItemStack.EMPTY;
        int anchor = (cover[anyIndex] == -1) ? anyIndex : cover[anyIndex];
        if (!isAnchor(anchor)) return ItemStack.EMPTY;
        ItemStack s = stacks.get(anchor).copy();
        int w = wOf(s), h = hOf(s);
        stacks.set(anchor, ItemStack.EMPTY);
        markArea(anchor, w, h, false);
        onContentsChanged(anchor);
        return s;
    }

    public boolean tryPlaceAt(int anchorIndex, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int w = wOf(stack), h = hOf(stack);
        if (!fitsRectAt(anchorIndex, w, h)) return false;
        stacks.set(anchorIndex, stack.copy());
        markArea(anchorIndex, w, h, true);
        cover[anchorIndex] = anchorIndex;
        onContentsChanged(anchorIndex);
        return true;
    }

    public boolean tryPlaceFirstFit(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int w = wOf(stack), h = hOf(stack);
        for (int i = 0; i < getSlots(); i++) {
            if (fitsRectAt(i, w, h)) {
                return tryPlaceAt(i, stack);
            }
        }
        return false;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (cover[slot] != -1 && cover[slot] != slot) return false;
        return canPlaceAt(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;
        if (simulate) return ItemStack.EMPTY;
        tryPlaceAt(slot, stack);
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (cover[slot] != -1 && cover[slot] != slot) return ItemStack.EMPTY;
        if (!isAnchor(slot)) return ItemStack.EMPTY;
        if (simulate) return stacks.get(slot).copy();
        return extractCluster(slot);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (cover[slot] != -1 && cover[slot] != slot) return ItemStack.EMPTY;
        return super.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (cover[slot] != -1 && cover[slot] != slot) return;
        if (isAnchor(slot)) {
            ItemStack cur = stacks.get(slot);
            int w = wOf(cur), h = hOf(cur);
            markArea(slot, w, h, false);
        }
        if (stack.isEmpty()) {
            stacks.set(slot, ItemStack.EMPTY);
            cover[slot] = -1;
            onContentsChanged(slot);
            return;
        }
        int w = wOf(stack), h = hOf(stack);
        if (!fitsRectAt(slot, w, h)) return;
        stacks.set(slot, stack.copy());
        markArea(slot, w, h, true);
        cover[slot] = slot;
        onContentsChanged(slot);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putInt("GW", width);
        tag.putInt("GH", height);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        this.cover = new int[getSlots()];
        java.util.Arrays.fill(this.cover, -1);
        for (int i = 0; i < getSlots(); i++) {
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()) {
                int w = wOf(s), h = hOf(s);
                cover[i] = i;
                markArea(i, w, h, true);
            }
        }
    }
}
