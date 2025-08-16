package fir.sec.tardoxinv.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 간단한 2D 인벤토리 핸들러(Forge IItemHandlerModifiable).
 * - 폭x높이 셀(1x1 기준)로 동작하도록 최소 구현
 * - serializeNBT/deserializeNBT 제공
 * - insertItem2D(...) 헬퍼 제공(2D 배치 프리뷰/검증 확장 가능)
 */
public class GridItemHandler2D implements IItemHandlerModifiable {
    private int width;
    private int height;
    private final List<ItemStack> stacks;

    public GridItemHandler2D(int w, int h) {
        this.width = Math.max(0, w);
        this.height = Math.max(0, h);
        this.stacks = new ArrayList<>(this.width * this.height);
        for (int i = 0; i < this.width * this.height; i++) stacks.add(ItemStack.EMPTY);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int size() { return width * height; }

    public boolean isAnchor(int index) {
        return index >= 0 && index < size(); // 간단 구현(멀티셀 아이템 미사용 시 anchor=자기칸)
    }

    public boolean canPlaceAt(int index, ItemStack stack) {
        if (index < 0 || index >= size()) return false;
        ItemStack cur = stacks.get(index);
        return cur.isEmpty() || (ItemStack.isSameItemSameTags(cur, stack) && cur.getCount() < cur.getMaxStackSize());
    }

    /** 2D용 편의 insert (여기선 1칸 기준으로 동작) */
    public ItemStack insertItem2D(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || size() == 0) return stack;
        // 1) 같은 아이템 합치기
        for (int i = 0; i < size(); i++) {
            ItemStack cur = stacks.get(i);
            if (cur.isEmpty()) continue;
            if (!ItemStack.isSameItemSameTags(cur, stack)) continue;
            int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
            if (can <= 0) continue;
            if (!simulate) cur.grow(can);
            stack.shrink(can);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        // 2) 빈 칸 채움
        for (int i = 0; i < size(); i++) {
            if (!stacks.get(i).isEmpty()) continue;
            int to = Math.min(stack.getCount(), stack.getMaxStackSize());
            if (!simulate) stacks.set(i, ItemHandlerHelper.copyStackWithSize(stack, to));
            stack.shrink(to);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    // ── IItemHandlerModifiable 구현 ──
    @Override public int getSlots() { return size(); }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return (slot >= 0 && slot < size()) ? stacks.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || slot >= size()) return stack;
        ItemStack cur = stacks.get(slot);
        if (cur.isEmpty()) {
            int to = Math.min(stack.getCount(), stack.getMaxStackSize());
            if (!simulate) stacks.set(slot, ItemHandlerHelper.copyStackWithSize(stack, to));
            return ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - to);
        }
        if (!ItemStack.isSameItemSameTags(cur, stack) || cur.getCount() >= cur.getMaxStackSize()) return stack;
        int can = Math.min(stack.getCount(), cur.getMaxStackSize() - cur.getCount());
        if (!simulate) cur.grow(can);
        return ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - can);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= size()) return ItemStack.EMPTY;
        ItemStack cur = stacks.get(slot);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        int can = Math.min(amount, cur.getCount());
        ItemStack out = ItemHandlerHelper.copyStackWithSize(cur, can);
        if (!simulate) {
            cur.shrink(can);
            if (cur.isEmpty()) stacks.set(slot, ItemStack.EMPTY);
        }
        return out;
    }

    @Override public int getSlotLimit(int slot) { return 64; }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    @Override public void setStackInSlot(int slot, ItemStack stack) { if (slot>=0 && slot<size()) stacks.set(slot, stack); }

    // ── NBT 직렬화 ──
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("W", width);
        tag.putInt("H", height);
        ListTag list = new ListTag();
        for (int i = 0; i < size(); i++) {
            ItemStack st = stacks.get(i);
            if (st.isEmpty()) continue;
            CompoundTag ct = new CompoundTag();
            ct.putInt("Slot", i);
            ct.put("Stack", st.save(new CompoundTag()));
            list.add(ct);
        }
        tag.put("Items", list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        this.width  = Math.max(0, tag.getInt("W"));
        this.height = Math.max(0, tag.getInt("H"));
        this.stacks.clear();
        for (int i = 0; i < size(); i++) stacks.add(ItemStack.EMPTY);
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int k = 0; k < list.size(); k++) {
            CompoundTag ct = list.getCompound(k);
            int slot = ct.getInt("Slot");
            if (slot < 0 || slot >= size()) continue;
            ItemStack st = ItemStack.of(ct.getCompound("Stack"));
            stacks.set(slot, st);
        }
    }
}
