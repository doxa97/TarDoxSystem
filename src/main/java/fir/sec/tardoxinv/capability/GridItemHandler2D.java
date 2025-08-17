package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * Width×Height 2D 인벤토리.
 * - 각 셀은 ItemStack을 보유
 * - 다칸 아이템은 앵커(좌상단) 기준으로 영역 전체를 같은 link_id 로 채움
 */
public class GridItemHandler2D extends ItemStackHandler {

    private int w;
    private int h;

    public GridItemHandler2D(int w, int h) {
        super(Math.max(0, w) * Math.max(0, h));
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
    }

    public int getWidth()  { return w; }
    public int getHeight() { return h; }

    /** 런타임에 그리드 크기를 바꿈(배낭 리사이즈). 기존 내용은 초기화됨. */
    public void setGridSize(int newW, int newH) {
        newW = Math.max(0, newW);
        newH = Math.max(0, newH);
        if (newW == this.w && newH == this.h) return;
        this.w = newW;
        this.h = newH;
        super.setSize(this.w * this.h); // ItemStackHandler 내부 스택 배열 리셋
        // 필요시 여기서 remap 로직을 덧붙일 수 있음(현재는 초기화)
    }

    public boolean inBounds(int index) {
        return index >= 0 && index < getSlots();
    }

    public int xyToIndex(int x, int y) { return y * w + x; }
    public int indexX(int index) { return index % w; }
    public int indexY(int index) { return index / w; }

    // ── NBT: 아이템 폭/높이 ──
    public static int stackW(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return (t != null && t.contains("Width")) ? Math.max(1, t.getInt("Width")) : 1;
    }
    public static int stackH(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return (t != null && t.contains("Height")) ? Math.max(1, t.getInt("Height")) : 1;
    }

    /** 앵커 index 에 stack 을 둘 수 있는지(경계/겹침 검사) */
    public boolean canPlaceAt(int index, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!inBounds(index)) return false;

        int sx = indexX(index), sy = indexY(index);
        int sw = stackW(stack),  sh = stackH(stack);

        if (sx + sw > w || sy + sh > h) return false;

        // 겹침 금지(단, 같은 link_id 는 자기 자리 복귀로 허용)
        for (int dy = 0; dy < sh; dy++) {
            for (int dx = 0; dx < sw; dx++) {
                int i = xyToIndex(sx + dx, sy + dy);
                ItemStack cur = getStackInSlot(i);
                if (!cur.isEmpty() && !sameLink(cur, stack)) return false;
            }
        }
        return true;
    }

    private static boolean sameLink(ItemStack a, ItemStack b) {
        CompoundTag ta = a.getTag(), tb = b.getTag();
        if (ta == null || tb == null) return false;
        if (!ta.contains("link_id") || !tb.contains("link_id")) return false;
        return ta.getString("link_id").equals(tb.getString("link_id"));
    }

    /** 앵커 index 에 stack 배치(영역 전체 채움). simulate 지원 */
    public ItemStack insertItem2D(int index, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!canPlaceAt(index, stack)) return stack;

        int sx = indexX(index), sy = indexY(index);
        int sw = stackW(stack),  sh = stackH(stack);

        // link_id 부여(없으면)
        CompoundTag t = stack.getOrCreateTag();
        if (!t.contains("link_id")) t.putString("link_id", UUID.randomUUID().toString());

        if (!simulate) {
            for (int dy = 0; dy < sh; dy++) {
                for (int dx = 0; dx < sw; dx++) {
                    int i = xyToIndex(sx + dx, sy + dy);
                    super.setStackInSlot(i, stack.copy()); // 동일 link_id로 채움
                }
            }
            onContentsChanged(index);
        }
        return ItemStack.EMPTY;
    }

    /** link_id 가 같은 모든 셀 제거(유령 점유 제거 핵심) */
    public void removeByLinkId(String linkId) {
        if (linkId == null || linkId.isEmpty()) return;
        for (int i = 0; i < getSlots(); i++) {
            ItemStack st = getStackInSlot(i);
            CompoundTag t = st.getTag();
            if (!st.isEmpty() && t != null && linkId.equals(t.getString("link_id"))) {
                super.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** 간단 앵커 판정(좌/상 이 같은 link_id 가 아니면 앵커) */
    public boolean isAnchor(int index) {
        ItemStack cur = getStackInSlot(index);
        if (cur.isEmpty()) return false;
        CompoundTag t = cur.getTag();
        String id = (t != null) ? t.getString("link_id") : "";
        int x = indexX(index), y = indexY(index);
        if (x > 0) {
            ItemStack left = getStackInSlot(index - 1);
            if (!left.isEmpty() && sameLink(left, cur)) return false;
        }
        if (y > 0) {
            ItemStack up = getStackInSlot(index - w);
            if (!up.isEmpty() && sameLink(up, cur)) return false;
        }
        return true;
    }

    // 저장 시 크기도 보존(참고용)
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag out = super.serializeNBT();
        out.putInt("W", w);
        out.putInt("H", h);
        return out;
    }
}
