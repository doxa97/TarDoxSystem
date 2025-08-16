package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

public class GridSlot extends SlotItemHandler {
    public enum Storage { BASE, BACKPACK }

    private final GridItemHandler2D grid;
    private final int gridIndex;
    private final Storage storage;

    public GridSlot(GridItemHandler2D handler, int gridIndex, int x, int y, Storage storage) {
        super(handler, gridIndex, x, y);
        this.grid = handler;
        this.gridIndex = gridIndex;
        this.storage = storage;
    }

    public int getGridIndex() { return gridIndex; }
    public Storage getStorage() { return storage; }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return grid.getStackInSlot(gridIndex).isEmpty() && grid.canPlaceAt(gridIndex, stack);
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return grid.isAnchor(gridIndex);
    }
}
