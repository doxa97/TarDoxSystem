package fir.sec.tardoxinv.menu;

/**
 * 호환용 브리지:
 * - 일부 코드가 fir.sec.tardoxinv.menu.GridSlot 을 임포트하므로
 * - 실제 구현( fir.sec.tardoxinv.menu.slot.GridSlot )을 그대로 상속해 노출한다.
 * - ⚠️ 여기서는 절대 enum이나 메서드를 재정의하지 않는다(타입 충돌 방지).
 */
public class GridSlot extends fir.sec.tardoxinv.menu.slot.GridSlot {
    public GridSlot(fir.sec.tardoxinv.menu.slot.GridSlot.Storage storage,
                    net.minecraftforge.items.IItemHandler handler,
                    int index, int x, int y) {
        super(storage, handler, index, x, y);
    }
}
