package fir.sec.tardoxinv.menu.slot;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 실제 2D 그리드 슬롯 구현.
 * - Storage (BASE/BACKPACK)
 * - gridIndex (그리드 인덱스)
 * - getGridIndex()/getIndex() 둘 다 제공(호환)
 * - storage()/index() 레코드 스타일 접근자도 제공(서버 코드 호환)
 */
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
    public int getGridIndex() { return gridIndex; } // AssignFromSlotPacket 호환
    public int getIndex() { return gridIndex; }

    // record 스타일 접근자(기존 서버 코드 호환)
    public Storage storage() { return storage; }
    public int index() { return gridIndex; }

    @Override public boolean mayPlace(ItemStack stack) { return true; }
}
