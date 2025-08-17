package fir.sec.tardoxinv.menu.slot;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 2D 그리드 전용 슬롯.
 * - Forge IItemHandler 기반으로 통일
 * - 앵커 슬롯에만 배치 허용 (GridItemHandler2D#canPlaceAt 사용)
 */
public class GridSlot extends SlotItemHandler {

    public GridSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    private int index() { return this.getSlotIndex(); }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (getItemHandler() instanceof GridItemHandler2D grid) {
            return grid.canPlaceAt(index(), stack);
        }
        // 혹시 일반 핸들러가 들어오는 상황이면 기본 동작
        return super.mayPlace(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        // 다칸 아이템도 앵커 슬롯에 "1" 스택만 들어가게 강제
        // (여러 개를 한 칸에 쌓는 개념을 쓰지 않으므로 1 고정)
        return 1;
    }
}
