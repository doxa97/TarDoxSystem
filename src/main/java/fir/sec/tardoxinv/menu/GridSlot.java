package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** 2D 그리드용 슬롯: 앵커 위치에만 배치 허용, 점유 영역과 충돌 시 거부.
 *  UI에서의 수동 배치는 '앵커가 비어있는 경우'에만 허용(기존 아이템 덮어쓰기 방지). */
public class GridSlot extends SlotItemHandler {
    public enum Storage { BASE, BACKPACK }

    private final GridItemHandler2D grid;
    private final int gridIndex;
    private final Storage storage;

    public GridSlot(GridItemHandler2D handler, int index, int x, int y, Storage storage){
        super(handler, index, x, y);
        this.grid = handler;
        this.gridIndex = index;
        this.storage = storage;
    }

    public Storage getStorage(){ return storage; }
    public int getGridIndex(){ return gridIndex; }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // 앵커가 비어있고, 영역 충돌이 없을 때만 배치 허용
        return grid.getStackInSlot(gridIndex).isEmpty() && grid.canPlaceAt(gridIndex, stack);
    }

    @Override
    public void set(ItemStack stack) {
        super.set(stack); // handler.setStackInSlot → 영역 점유 처리
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        // 앵커 슬롯만 꺼낼 수 있음
        return grid.isAnchor(this.getSlotIndex());
    }
}
