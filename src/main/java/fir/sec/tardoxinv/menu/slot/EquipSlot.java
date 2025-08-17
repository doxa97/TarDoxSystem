package fir.sec.tardoxinv.menu.slot;

import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/** 장착칸 표기용 1×1 슬롯. 오버레이가 테두리를 그려준다. */
public class EquipSlot extends SlotItemHandler {
    public enum Kind { HEADSET, VEST, HELMET, BACKPACK, PRIM1, PRIM2, SEC, MELEE }
    private final Kind kind;
    public EquipSlot(IItemHandler handler, int index, int x, int y, Kind kind) {
        super(handler, index, x, y);
        this.kind = kind;
    }
    public Kind getKind() { return kind; }
}
