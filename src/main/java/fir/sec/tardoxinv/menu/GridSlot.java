package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 2D 그리드용 슬롯
 * - 빈 칸이면 grid.canPlaceAt 로 배치 가능 여부 판단
 * - 앵커(실제 아이템이 들어있는 칸)만 픽업 허용
 * - AssignFromSlotPacket 에서 필요로 하는 Storage 구분자/게터 제공
 */
public class GridSlot extends Slot {

    public enum Storage { BASE, BACKPACK }

    private final GridItemHandler2D grid;
    private final int gridIndex;
    private final Storage storage;

    /** 기본: BASE 스토리지로 간주 */
    public GridSlot(GridItemHandler2D grid, int gridIndex, int x, int y) {
        this(Storage.BASE, grid, gridIndex, x, y);
    }

    /** 명시적 스토리지 지정 */
    public GridSlot(Storage storage, GridItemHandler2D grid, int gridIndex, int x, int y) {
        // Slot 은 Container 기반이지만 여기서는 화면/클릭 판정만 사용하므로
        // 1칸짜리 더미 컨테이너를 전달
        super(new net.minecraft.world.SimpleContainer(1), 0, x, y);
        this.grid = grid;
        this.gridIndex = gridIndex;
        this.storage = storage;
    }

    public int getGridIndex() { return gridIndex; }
    public Storage getStorage() { return storage; }

    /** 커서 아이템을 둘 수 있는지(해당 위치가 비어 있고, 크기 배치가 가능한지) */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return grid.getStackInSlot(gridIndex).isEmpty() && grid.canPlaceAt(gridIndex, stack);
    }

    /** 앵커 슬롯만 픽업 허용 */
    @Override
    public boolean mayPickup(Player player) {
        return grid.isAnchor(gridIndex);
    }
}
