package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.menu.slot.GridSlot.Storage;

/**
 * 호환용 브리지:
 * - 일부 코드가 fir.sec.tardoxinv.menu.GridSlot 을 임포트/생성자 사용
 * - 실제 구현은 fir.sec.tardoxinv.menu.slot.GridSlot 이고,
 *   여기서는 생성자 시그니처를 사용처에 맞춰 제공한다.
 */
public class GridSlot extends fir.sec.tardoxinv.menu.slot.GridSlot {

    /** 사용처: new GridSlot(handler, index, x, y, Storage) */
    public GridSlot(net.minecraftforge.items.IItemHandler handler,
                    int index, int x, int y, Storage storage) {
        super(storage, handler, index, x, y);
    }

    /** 사용처: new GridSlot(Storage, handler, index, x, y) (직접 호출 시) */
    public GridSlot(Storage storage,
                    net.minecraftforge.items.IItemHandler handler,
                    int index, int x, int y) {
        super(storage, handler, index, x, y);
    }
}
