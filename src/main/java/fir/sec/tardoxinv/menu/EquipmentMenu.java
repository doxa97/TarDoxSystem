package fir.sec.tardoxinv.menu;

import fir.sec.tardoxinv.capability.GridItemHandler2D;
import fir.sec.tardoxinv.capability.ModCapabilities;
import fir.sec.tardoxinv.capability.PlayerEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 커스텀 장비/배낭 컨테이너
 *
 * ✦ 바닐라 3×9/핫바 슬롯을 추가하지 않는다.
 * ✦ 장착 슬롯 7개 + 기본 2×2 + (배낭 착용 시) 배낭 그리드만 추가한다.
 * ✦ shift-클릭 이동 비활성.
 */
public class EquipmentMenu extends AbstractContainerMenu {

    // 화면 크기 (스킨 교체 시 숫자만 조절)
    public static final int TEX_W = 256;
    public static final int TEX_H = 256;

    // ───── 슬롯 배치 좌표 ─────
    // 장착 슬롯 (좌상단 기준)
    private static final int EQUIP_X = 16;
    private static final int EQUIP_Y = 20;
    private static final int EQUIP_SP = 20; // 간격

    // 무기 슬롯 4개 (주1, 주2, 보조, 근접)
    private static final int WEAPON_X = 16;
    private static final int WEAPON_Y = 90;
    private static final int WEAPON_SP = 20;

    // 기본 2×2
    private static final int BASE_X = 120;
    private static final int BASE_Y = 90;

    // 배낭 그리드 좌상단
    private static final int BACKPACK_X = 120;
    private static final int BACKPACK_Y = 20;

    private final PlayerEquipment eq;

    public EquipmentMenu(int id, Inventory inv) {
        super(ModMenus.EQUIPMENT_MENU.get(), id);

        // PlayerEquipment 가져오기 (없으면 임시 객체)
        this.eq = inv.player.getCapability(ModCapabilities.EQUIPMENT).orElseGet(PlayerEquipment::new);

        // 1) 장착 슬롯 7개(헤드셋/방탄복/헬멧/배낭 + 무기4)
        //   equipment 핸들러의 인덱스는 PlayerEquipment 상수와 1:1
        var equipHandler = eq.getEquipment();
        // 착용 장비 3 + 배낭 (세로)
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_HEADSET, EQUIP_X, EQUIP_Y + 0 * EQUIP_SP));
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_VEST,    EQUIP_X, EQUIP_Y + 1 * EQUIP_SP));
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_HELMET,  EQUIP_X, EQUIP_Y + 2 * EQUIP_SP));
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_MELEE,   EQUIP_X, EQUIP_Y + 3 * EQUIP_SP)); // 근접을 여기 두고,

        // 무기 3개 + 주무기2(가로)
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_PRIM1, WEAPON_X + 0 * WEAPON_SP, WEAPON_Y));
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_PRIM2, WEAPON_X + 1 * WEAPON_SP, WEAPON_Y));
        this.addSlot(equipSlot(equipHandler, PlayerEquipment.SLOT_SEC,   WEAPON_X + 2 * WEAPON_SP, WEAPON_Y));
        // 근접을 위에서 넣었으니 여기선 생략

        // 2) 기본 2×2
        GridItemHandler2D base = eq.getBase2x2();
        addGridSlots(GridSlot.Storage.BASE, base, 2, 2, BASE_X, BASE_Y);

        // 3) 배낭 그리드(착용 시에만)
        GridItemHandler2D bp = eq.getBackpack();
        if (bp != null && eq.getBackpackWidth() > 0 && eq.getBackpackHeight() > 0) {
            addGridSlots(GridSlot.Storage.BACKPACK, bp,
                    eq.getBackpackWidth(), eq.getBackpackHeight(),
                    BACKPACK_X, BACKPACK_Y);
        }
    }

    /** 호환용 오버로드 (w,h는 내부에서 capability로 다시 읽음) */
    public EquipmentMenu(int id, Inventory inv, int w, int h) { this(id, inv); }

    @Override public boolean stillValid(Player player) { return true; }

    /** shift-클릭 이동 금지 */
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    // ───── helpers ─────

    private Slot equipSlot(IItemHandler handler, int idx, int x, int y) {
        // 추후 장비 타입 제한이 필요하면 이 Slot을 별도 클래스로 빼고 mayPlace 필터링
        return new SlotItemHandler(handler, idx, x, y);
    }

    private void addGridSlots(GridSlot.Storage storage, IItemHandler handler,
                              int w, int h, int left, int top) {
        int i = 0;
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++, i++) {
                this.addSlot(new GridSlot(storage, handler, i, left + c * 18, top + r * 18));
            }
        }
    }
}
