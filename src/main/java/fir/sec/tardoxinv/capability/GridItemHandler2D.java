package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Arrays;
import java.util.UUID;

/**
 * Width×Height 2D 인벤토리(앵커 단일 저장 방식).
 * - 앵커 칸(좌상단)에만 실제 ItemStack 저장
 * - 나머지 칸은 내부 coveredBy 테이블로 '점유'만 표시
 * - 비앵커 칸의 getStackInSlot()은 항상 EMPTY 반환
 */
public class GridItemHandler2D extends ItemStackHandler {

    private int w, h;              // 전체 그리드 크기
    private int[] coveredBy;       // 각 칸을 점유하는 앵커 인덱스(-1: 비점유)

    public GridItemHandler2D(int w, int h) {
        super(Math.max(0, w) * Math.max(0, h));
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
        this.coveredBy = new int[this.w * this.h];
        Arrays.fill(this.coveredBy, -1);
        rebuildAnchors(); // 저장 복구 시 유령 데이터 정리
        onLoad();
    }

    public int getWidth()  { return w; }
    public int getHeight() { return h; }

    /** 런타임 리사이즈: 기존 내용 초기화(필요시 remap 추가 가능) */
    public void setGridSize(int newW, int newH) {
        newW = Math.max(0, newW);
        newH = Math.max(0, newH);
        if (newW == this.w && newH == this.h) return;
        this.w = newW;
        this.h = newH;
        super.setSize(this.w * this.h);
        this.coveredBy = new int[this.w * this.h];
        Arrays.fill(this.coveredBy, -1);
        // 내용 리셋 정책. 필요하면 remap 로직을 여기에 추가.
        onLoad();
    }

    /* ───────────────────── 헬퍼 ───────────────────── */
    private boolean inBounds(int index){ return index >= 0 && index < getSlots(); }
    private int xyToIndex(int x, int y){ return y * w + x; }
    private int indexX(int index){ return index % w; }
    private int indexY(int index){ return index / w; }
    private boolean inBoundsXY(int x, int y){ return x >= 0 && y >= 0 && x < w && y < h; }
    @Override
    public void onLoad() {
        // ItemStackHandler가 NBT를 읽은 직후 호출됨
        // 로드된 스택을 기준으로 점유 테이블을 다시 구축
        rebuildAnchors();
    }
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // 저장된 W/H를 먼저 읽어 사이즈를 맞춘다 (그 다음에 스택 로드)
        int nw = nbt.contains("W") ? nbt.getInt("W") : this.w;
        int nh = nbt.contains("H") ? nbt.getInt("H") : this.h;
        nw = Math.max(1, nw);
        nh = Math.max(1, nh);

        this.w = nw;
        this.h = nh;

        // setSize는 부모(아이템스택핸들러) 보호 메서드. 여기서 먼저 슬롯 수를 맞춘다.
        super.setSize(this.w * this.h);

        // 점유 테이블 초기화
        this.coveredBy = new int[this.w * this.h];
        java.util.Arrays.fill(this.coveredBy, -1);

        // 실제 아이템 스택 로딩
        super.deserializeNBT(nbt);

        // onLoad()도 불리지만, 혹시 모를 구현 차이를 고려해 한 번 더 안전하게 재구축
        rebuildAnchors();
    }

    public static int stackW(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return (t != null && t.contains("Width")) ? Math.max(1, t.getInt("Width")) : 1;
    }

    public static int stackH(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return (t != null && t.contains("Height")) ? Math.max(1, t.getInt("Height")) : 1;
    }

    private static boolean sameLink(ItemStack a, ItemStack b) {
        CompoundTag ta = a.getTag(), tb = b.getTag();
        if (ta == null || tb == null) return false;
        if (!ta.contains("link_id") || !tb.contains("link_id")) return false;
        return ta.getString("link_id").equals(tb.getString("link_id"));
    }

    private static void ensureLinkId(ItemStack s) {
        CompoundTag t = s.getOrCreateTag();
        if (!t.contains("link_id")) t.putString("link_id", UUID.randomUUID().toString());
    }

    /* ───────────────── 배치/점유 논리 ───────────────── */

    /** 경계·겹침 검사: index를 앵커로 stack을 둘 수 있는가 */
    public boolean canPlaceAt(int index, ItemStack stack) {
        if (stack.isEmpty() || !inBounds(index)) return false;
        int sx = indexX(index), sy = indexY(index);
        int sw = stackW(stack), sh = stackH(stack);
        if (sx + sw > w || sy + sh > h) return false;

        // 🔧 스테일 커버리지 무시
        for (int dy = 0; dy < sh; dy++) {
            for (int dx = 0; dx < sw; dx++) {
                int i = xyToIndex(sx + dx, sy + dy);
                int a = coveredBy[i];
                if (a != -1 && a != index) {
                    // 해당 앵커가 실제 스택이 없으면 유령 점유 → 무시
                    if (super.getStackInSlot(a).isEmpty()) continue;
                    return false;
                }
            }
        }
        return true;
    }


    /** 앵커 발자국 마킹(실스택은 앵커에만 존재) */
    private void markFootprint(int anchor, ItemStack stack) {
        int sw = stackW(stack), sh = stackH(stack);
        int ax = indexX(anchor), ay = indexY(anchor);
        for (int dy = 0; dy < sh; dy++) {
            for (int dx = 0; dx < sw; dx++) {
                int x = ax + dx, y = ay + dy;
                if (!inBoundsXY(x, y)) continue;
                int i = xyToIndex(x, y);
                coveredBy[i] = anchor;
                if (i != anchor) {
                    // 비앵커 칸에는 실제 스택이 절대 놓이지 않도록 보정
                    super.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /** 앵커 발자국 해제 */
    private void clearFootprint(int anchor, ItemStack anchorStack) {
        if (anchorStack == null) anchorStack = ItemStack.EMPTY;
        int sw = stackW(anchorStack), sh = stackH(anchorStack);
        int ax = indexX(anchor), ay = indexY(anchor);
        for (int dy = 0; dy < sh; dy++) {
            for (int dx = 0; dx < sw; dx++) {
                int x = ax + dx, y = ay + dy;
                if (!inBoundsXY(x, y)) continue;
                int i = xyToIndex(x, y);
                if (coveredBy[i] == anchor) coveredBy[i] = -1;
            }
        }
    }

    /** 유령 복제 상태 등 기존 내용을 스캔해 ‘앵커’만 남기고 재마킹 */
    private void rebuildAnchors() {
        Arrays.fill(coveredBy, -1);
        // 1) 원시 스캔: 좌/상에 같은 link_id가 없으면 앵커 후보
        for (int i = 0; i < getSlots(); i++) {
            ItemStack s = super.getStackInSlot(i); // 원시 접근
            if (s.isEmpty()) continue;

            int x = indexX(i), y = indexY(i);
            boolean leftSame = (x > 0) && !super.getStackInSlot(i - 1).isEmpty()
                    && sameLink(super.getStackInSlot(i - 1), s);
            boolean upSame = (y > 0) && !super.getStackInSlot(i - w).isEmpty()
                    && sameLink(super.getStackInSlot(i - w), s);
            if (leftSame || upSame) continue; // 비앵커는 스킵

            // 2) 앵커로 인정 후 발자국 마킹 (겹치면 나중 앵커가 못 마킹)
            if (canPlaceAt(i, s)) {
                ensureLinkId(s);
                markFootprint(i, s);
            }
        }
        // 3) 앵커가 아닌 칸의 실스택은 모두 제거
        for (int i = 0; i < getSlots(); i++) {
            if (coveredBy[i] != i) super.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /* ─────────────── ItemStackHandler 오버라이드 ─────────────── */

    /** 비앵커 칸은 항상 빈칸처럼 보이게 함 */
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!inBounds(slot)) return ItemStack.EMPTY;
        if (coveredBy[slot] == slot) return super.getStackInSlot(slot);
        return ItemStack.EMPTY;
    }

    /** set: 항상 앵커 기준. 비우면 발자국 해제 */
    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!inBounds(slot)) return;
        purgeStaleCoverage(); // 🔧 배치 전 청소

        int anchor = slot;   // 🔧 라우팅 금지: 무조건 이 슬롯을 앵커로 사용
        ItemStack cur = super.getStackInSlot(anchor);

        // 비우기
        if (stack == null || stack.isEmpty()) {
            if (!cur.isEmpty()) {
                clearFootprint(anchor, cur);
                super.setStackInSlot(anchor, ItemStack.EMPTY);
                onContentsChanged(anchor);
            } else {
                // 유령 점유만 남았을 수 있으니 해당 앵커 지문도 정리
                if (coveredBy[anchor] == anchor) clearFootprint(anchor, ItemStack.EMPTY);
                coveredBy[anchor] = -1;
            }
            return;
        }

        // 채우기
        if (!canPlaceAt(anchor, stack)) return;
        if (!cur.isEmpty()) clearFootprint(anchor, cur);

        ItemStack copy = stack.copy();
        ensureLinkId(copy);
        super.setStackInSlot(anchor, copy);
        markFootprint(anchor, copy);
        onContentsChanged(anchor);
    }


    /** insert: 전체 스택을 앵커에 배치(남는 것 없음) */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !inBounds(slot)) return ItemStack.EMPTY;
        purgeStaleCoverage(); // 🔧

        int anchor = slot;    // 🔧 이 슬롯을 앵커로 고정
        if (!canPlaceAt(anchor, stack)) return stack;
        if (simulate) return ItemStack.EMPTY;

        ItemStack cur = super.getStackInSlot(anchor);
        if (!cur.isEmpty()) clearFootprint(anchor, cur);

        ItemStack copy = stack.copy();
        ensureLinkId(copy);
        super.setStackInSlot(anchor, copy);
        markFootprint(anchor, copy);
        onContentsChanged(anchor);
        return ItemStack.EMPTY;
    }


    /** extract: 앵커에서만 추출, 0되면 발자국 해제 */
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !inBounds(slot)) return ItemStack.EMPTY;
        purgeStaleCoverage(); // 🔧

        int anchor = coveredBy[slot];
        if (anchor == -1) anchor = slot;              // 커버리지 없으면 슬롯 자체가 앵커
        if (anchor != slot && super.getStackInSlot(anchor).isEmpty()) {
            anchor = slot; // 유령 앵커면 슬롯을 앵커로 보정
        }

        ItemStack cur = super.getStackInSlot(anchor);
        if (cur.isEmpty()) return ItemStack.EMPTY;

        ItemStack out = cur.copy();
        out.setCount(Math.min(amount, cur.getCount()));

        if (!simulate) {
            cur.shrink(out.getCount());
            if (cur.isEmpty()) {
                clearFootprint(anchor, out);
                super.setStackInSlot(anchor, ItemStack.EMPTY);
            } else {
                super.setStackInSlot(anchor, cur);
                markFootprint(anchor, cur);
            }
            onContentsChanged(anchor);
        }
        return out;
    }


    /* ─────────────── 추가 유틸 (호환용) ─────────────── */

    /** 기존 렌더러에서 사용 */
    public boolean isAnchor(int index) {
        return inBounds(index) && coveredBy[index] == index && !super.getStackInSlot(index).isEmpty();
    }

    public boolean isOccupied(int index) {
        return inBounds(index) && coveredBy[index] != -1;
    }

    public int anchorOf(int index) {
        return inBounds(index) ? coveredBy[index] : -1;
    }

    /** 저장/로딩 시 그리드 크기 보존 */
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag out = super.serializeNBT();
        out.putInt("W", w);
        out.putInt("H", h);
        return out;
    }
    // coveredBy가 가리키는 앵커가 실제로 스택을 갖고 있지 않으면 비움
    private void purgeStaleCoverage() {
        for (int i = 0; i < coveredBy.length; i++) {
            int a = coveredBy[i];
            if (a != -1) {
                if (a < 0 || a >= getSlots() || super.getStackInSlot(a).isEmpty()) {
                    coveredBy[i] = -1;
                }
            }
        }
    }

}
