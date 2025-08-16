package fir.sec.tardoxinv.menu.slot;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 2D 그리드 슬롯. Storage(BACKPACK/BASE) + 인덱스를 보존.
 * (오버레이/바인딩 추적에 사용)
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
    public int getIndex() { return gridIndex; }

    // record 스타일 메서드명도 제공(서버 이벤트에서 쓰임)
    public Storage storage() { return storage; }
    public int index() { return gridIndex; }

    @Override public boolean mayPlace(ItemStack stack) { return true; }
}
