package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 2D 그리드 인벤토리. 아이템 NBT의 Width/Height를 읽어 앵커 슬롯(좌상단) 기준으로
 * w×h 영역을 점유한다. 앵커 슬롯에만 실스택을 저장하며, 나머지 칸은 빈칸처럼 보이게 한다.
 */
public class GridItemHandler2D extends ItemStackHandler {
    private int gridW, gridH;
    // 각 칸이 어느 앵커(slot index)를 따르는지 기록(-1이면 비어있음)
    private int[] anchorOf;

    public GridItemHandler2D(int w, int h) {
        super(Math.max(0, w*h));
        setGridSize(w, h);
    }

    public void setGridSize(int w, int h) {
        this.gridW = Math.max(0, w);
        this.gridH = Math.max(0, h);
        int size = gridW * gridH;
        if (size != this.stacks.size()) {
            // ItemStackHandler.stacks 는 NonNullList 여야 한다
            this.stacks = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY);
        }
        this.anchorOf = new int[size];
        java.util.Arrays.fill(this.anchorOf, -1);
        rebuildAnchors();
        onLoad();
    }

    public int getGridW(){ return gridW; }
    public int getGridH(){ return gridH; }

    public int xyToIndex(int x, int y){ return x + y * gridW; }
    public int idxX(int idx){ return idx % gridW; }
    public int idxY(int idx){ return idx / gridW; }

    // ---- 배치 가능성 ----
    public static int getItemW(ItemStack st){
        return st.hasTag() ? Math.max(1, st.getTag().getInt("Width")) : 1;
    }
    public static int getItemH(ItemStack st){
        return st.hasTag() ? Math.max(1, st.getTag().getInt("Height")) : 1;
    }

    public boolean canPlaceAt(int anchorIdx, ItemStack stack){
        if (stack.isEmpty()) return true;
        int w = getItemW(stack), h = getItemH(stack);
        int ax = idxX(anchorIdx), ay = idxY(anchorIdx);
        if (ax + w > gridW || ay + h > gridH) return false;

        for (int dx=0; dx<w; dx++){
            for (int dy=0; dy<h; dy++){
                int idx = xyToIndex(ax+dx, ay+dy);
                int a = anchorOf[idx];
                // 다른 앵커가 점유 중이면 불가 (자기 앵커는 허용)
                if (a != -1 && a != anchorIdx) return false;
            }
        }
        return true;
    }

    // ---- 점유/해제 ----
    private void occupy(int anchorIdx, ItemStack stack){
        int w = getItemW(stack), h = getItemH(stack);
        int ax = idxX(anchorIdx), ay = idxY(anchorIdx);
        for (int dx=0; dx<w; dx++){
            for (int dy=0; dy<h; dy++){
                int idx = xyToIndex(ax+dx, ay+dy);
                anchorOf[idx] = anchorIdx;
                if (idx != anchorIdx) super.setStackInSlot(idx, ItemStack.EMPTY);
            }
        }
    }

    private void clearRegionOfAnchor(int anchorIdx){
        for (int i=0;i<anchorOf.length;i++){
            if (anchorOf[i] == anchorIdx) anchorOf[i] = -1;
        }
        super.setStackInSlot(anchorIdx, ItemStack.EMPTY);
    }

    private void rebuildAnchors(){
        java.util.Arrays.fill(anchorOf, -1);
        for (int i=0;i<stacks.size();i++){
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()){
                if (canPlaceAt(i, s)) occupy(i, s);
                else super.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** 자동 배치: 반드시 '앵커 슬롯이 비어있는 곳'에만 넣는다(기존 아이템 덮어쓰기 방지) */
    public boolean insertGrid(ItemStack stack){
        if (stack.isEmpty()) return false;
        for (int i=0;i<getSlots();i++){
            if (!getStackInSlot(i).isEmpty()) continue; // 앵커가 비어있는 곳만
            if (canPlaceAt(i, stack)){
                setStackInSlot(i, stack.copy());
                return true;
            }
        }
        return false;
    }

    /** 앵커 슬롯에만 실스택을 노출 */
    @Override
    public ItemStack getStackInSlot(int slot){
        if (anchorOf[slot] == slot) return super.getStackInSlot(slot);
        return ItemStack.EMPTY;
    }

    /** 수동 배치: canPlaceAt 만족 시 기존 앵커면 먼저 해제 후 점유 */
    @Override
    public void setStackInSlot(int slot, ItemStack stack){
        if (stack.isEmpty()){
            if (isAnchor(slot)) clearRegionOfAnchor(slot);
            return;
        }
        if (!canPlaceAt(slot, stack)) return;
        if (isAnchor(slot)) clearRegionOfAnchor(slot);
        super.setStackInSlot(slot, stack);
        occupy(slot, stack);
        onContentsChanged(slot);
    }

    public boolean isAnchor(int idx){ return idx >=0 && idx < anchorOf.length && anchorOf[idx] == idx && !super.getStackInSlot(idx).isEmpty(); }
    public boolean isOccupied(int idx){ return idx >=0 && idx < anchorOf.length && anchorOf[idx] != -1; }

    @Override public CompoundTag serializeNBT(){ return super.serializeNBT(); }
    @Override public void deserializeNBT(CompoundTag nbt){ super.deserializeNBT(nbt); rebuildAnchors(); onLoad(); }
}
