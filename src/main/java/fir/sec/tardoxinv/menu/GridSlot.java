package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 2D 그리드 전용 슬롯 (후방 호환 유지)
 * - 패키지: fir.sec.tardoxinv.menu (기존 import 경로 유지)
 * - SlotItemHandler 기반
 * - 기존 코드가 기대하는 Storage enum, getGridIndex(), getStorage() 제공
 */
public class GridSlot extends SlotItemHandler {

    public enum Storage { BASE, BACKPACK, EQUIPMENT, UTILITY }

    private final Storage storage;

    /** 기존 EquipmentMenu 등에서 사용하던 시그니처 */
    public GridSlot(IItemHandler handler, int index, int x, int y, Storage storage) {
        super(handler, index, x, y);
        this.storage = storage;
    }

    public Storage getStorage() { return storage; }

    /** 앵커 인덱스 == 슬롯 인덱스 */
    public int getGridIndex() { return this.getSlotIndex(); }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (getItemHandler() instanceof GridItemHandler2D gh) {
            return gh.canPlaceAt(getGridIndex(), stack);
        }
        return super.mayPlace(stack);
    }
}
