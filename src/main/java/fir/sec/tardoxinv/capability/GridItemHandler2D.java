package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 가로×세로 격자 인벤토리.
 * - 한 아이템은 (Width×Height) 태그 크기만큼 차지한다.
 * - 앵커(Anchor) 칸에만 실제 스택이 들어가며, 나머지 영역은 비어 있어야 한다.
 */
public class GridItemHandler2D extends ItemStackHandler {
    private int width;
    private int height;

    // 각 셀의 앵커 인덱스(해당 셀이 비어있으면 -1, 자신이 앵커면 자신의 인덱스)
    private int[] anchorOf;

    public GridItemHandler2D(int w, int h) {
        super(Math.max(0, w * h));
        this.width = Math.max(0, w);
        this.height = Math.max(0, h);
        this.anchorOf = new int[getSlots()];
        clearAnchors();
    }

    public int getGridWidth() { return width; }
    public int getGridHeight() { return height; }

    /** index가 앵커인지 여부 */
    public boolean isAnchor(int index) {
        return index >= 0 && index < getSlots() && anchorOf[index] == index;
    }

    /** 앵커가 비어 있거나, 영역 전체가 비어 있으면 해당 앵커에 배치 가능 */
    public boolean canPlaceAt(int anchorIndex, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (anchorIndex < 0 || anchorIndex >= getSlots()) return false;

        int w = Math.max(1, stack.getOrCreateTag().getInt("Width"));
        int h = Math.max(1, stack.getOrCreateTag().getInt("Height"));

        int ax = anchorIndex % width;
        int ay = anchorIndex / width;

        if (ax + w > width || ay + h > height) return false;

        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int idx = (ay + dy) * width + (ax + dx);
                if (anchorOf[idx] != -1) {
                    // 다른 아이템 영역과 겹침
                    if (isAnchor(anchorIndex)) {
                        // 같은 앵커의 기존 영역 위로 겹치려는 경우는 허용(재배치)
                        if (anchorOf[idx] != anchorIndex) return false;
                    } else {
                        return false;
                    }
                }
                if (idx != anchorIndex && !super.getStackInSlot(idx).isEmpty()) return false;
            }
        }
        return true;
    }

    /** 그리드 크기 재설정(내용 비움) */
    public void resize(int w, int h) {
        this.width = Math.max(0, w);
        this.height = Math.max(0, h);
        int size = Math.max(0, this.width * this.height);
        this.stacks = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);
        this.anchorOf = new int[size];
        clearAnchors();
        onContentsChanged(0);
    }

    private void clearAnchors() {
        for (int i = 0; i < anchorOf.length; i++) anchorOf[i] = -1;
    }

    /** 앵커에 스택을 넣으면 해당 영역을 해당 앵커로 마킹 */
    @Override
    public void setStackInSlot(int index, ItemStack stack) {
        // 기존 영역을 지운다(해당 인덱스가 기존 앵커였다면)
        if (index >= 0 && index < getSlots() && isAnchor(index)) {
            clearArea(index);
        }

        if (!stack.isEmpty()) {
            int w = Math.max(1, stack.getOrCreateTag().getInt("Width"));
            int h = Math.max(1, stack.getOrCreateTag().getInt("Height"));
            if (!canPlaceAt(index, stack)) return; // 불가능하면 무시

            // 앵커/영역 마킹
            int ax = index % width;
            int ay = index / width;
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    int idx = (ay + dy) * width + (ax + dx);
                    anchorOf[idx] = index;
                    if (idx != index) super.setStackInSlot(idx, ItemStack.EMPTY);
                }
            }
            anchorOf[index] = index;
        }
        super.setStackInSlot(index, stack);
    }

    /** index가 속한 영역(해당 앵커) 전체를 비움 */
    public void clearArea(int index) {
        if (index < 0 || index >= getSlots()) return;
        int anchor = anchorOf[index];
        if (anchor == -1) return;

        ItemStack anchorStack = super.getStackInSlot(anchor);
        int w = 1, h = 1;
        if (!anchorStack.isEmpty()) {
            w = Math.max(1, anchorStack.getOrCreateTag().getInt("Width"));
            h = Math.max(1, anchorStack.getOrCreateTag().getInt("Height"));
        }
        int ax = anchor % width, ay = anchor / width;
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int idx = (ay + dy) * width + (ax + dx);
                if (idx >= 0 && idx < getSlots()) {
                    anchorOf[idx] = -1;
                    if (idx != anchor) super.setStackInSlot(idx, ItemStack.EMPTY);
                }
            }
        }
        super.setStackInSlot(anchor, ItemStack.EMPTY);
    }

    /** 비앵커 칸은 항상 빈 칸처럼 보이게 한다(보여주기 전용) */
    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= getSlots()) return ItemStack.EMPTY;
        if (anchorOf[index] == -1) return super.getStackInSlot(index);
        return (anchorOf[index] == index) ? super.getStackInSlot(index) : ItemStack.EMPTY;
    }

    /** 저장: 앵커 칸만 저장한다 */
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("W", width);
        tag.putInt("H", height);
        ListTag list = new ListTag();
        for (int i = 0; i < getSlots(); i++) {
            if (isAnchor(i)) {
                ItemStack s = super.getStackInSlot(i);
                if (!s.isEmpty()) {
                    CompoundTag it = new CompoundTag();
                    it.putInt("I", i);
                    list.add(s.save(it));
                }
            }
        }
        tag.put("Items", list);
        return tag;
    }

    /** 로드: 앵커만 복원 후 영역 마킹 */
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        int w = nbt.getInt("W");
        int h = nbt.getInt("H");
        if (w <= 0 || h <= 0) { resize(0, 0); return; }
        resize(w, h);

        ListTag list = nbt.getList("Items", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag it = list.getCompound(i);
            int idx = it.getInt("I");
            if (idx >= 0 && idx < getSlots()) {
                ItemStack s = ItemStack.of(it);
                setStackInSlot(idx, s);
            }
        }
    }
}
