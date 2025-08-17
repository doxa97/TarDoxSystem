package fir.sec.tardoxinv.menu.slot;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/** 2D 그리드용 슬롯. 배치 가능 여부는 GridItemHandler2D 에 위임. */
public class GridSlot extends SlotItemHandler {

    public enum Storage { BASE, BACKPACK }

    private final Storage storage;
    private final int gridIndex;

    public GridSlot(Storage storage, IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
        this.storage = storage;
        this.gridIndex = index;
    }

    public Storage getStorage() { return storage; }
    public int getGridIndex() { return gridIndex; }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (getItemHandler() instanceof GridItemHandler2D gh) {
            return gh.canPlaceAt(gridIndex, stack);
        }
        return super.mayPlace(stack);
    }
}
