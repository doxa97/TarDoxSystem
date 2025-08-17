package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 2D 그리드 전용 슬롯 (후방 호환 버전)
 * - 패키지: fir.sec.tardoxinv.menu (기존 코드가 import 하는 경로 유지)
 * - Forge IItemHandler 기반(SlotItemHandler 상속)으로 통일
 * - 기존 코드에서 기대하는 Storage enum, getGridIndex(), getStorage() 제공
 */
public class GridSlot extends SlotItemHandler {

    /** 기존 코드가 참조하는 스토리지 구분자 */
    public enum Storage {
        BASE,
        BACKPACK,
        EQUIPMENT,   // 필요시 확장됨(기존 코드에서 안 써도 무방)
        UTILITY      // 필요시 확장됨
    }

    private final Storage storage;

    /**
     * 기존 EquipmentMenu 등에서 사용하는 시그니처:
     * new GridSlot(handler, index, x, y, GridSlot.Storage.BASE)
     */
    public GridSlot(IItemHandler handler, int index, int x, int y, Storage storage) {
        super(handler, index, x, y);
        this.storage = storage;
    }

    /** 기존 렌더러/핫바 바인딩 코드가 사용하는 getter */
    public Storage getStorage() {
        return storage;
    }

    /**
     * 그리드 앵커 인덱스 반환.
     * 현재 구조에선 슬롯 인덱스 == 그리드 앵커 인덱스로 동작합니다.
     * (핸들러가 내부적으로 다칸 아이템을 관리)
     */
    public int getGridIndex() {
        return this.getSlotIndex();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty()) return false;
        IItemHandler h = getItemHandler();
        if (h instanceof GridItemHandler2D grid) {
            // 앵커 슬롯 유효성(경계/겹침/금지영역 등)은 핸들러에게 위임
            return grid.canPlaceAt(getGridIndex(), stack);
        }
        // 혹시 일반 핸들러가 들어오면 기본 동작
        return super.mayPlace(stack);
    }

}
