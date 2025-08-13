package fir.sec.tardoxinv.menu;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * 특정 slot_type 태그를 가진 아이템만 허용하는 커스텀 슬롯
 */
public class CustomSlot extends SlotItemHandler {

    private final String allowedType; // 허용할 slot_type (null이면 모든 아이템 허용)

    public CustomSlot(IItemHandler handler, int index, int xPosition, int yPosition, String allowedType) {
        super(handler, index, xPosition, yPosition);
        this.allowedType = allowedType;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // allowedType 지정된 경우 해당 slot_type과 일치할 때만 허용
        if (allowedType == null) {
            return true;
        }
        if (!stack.hasTag()) return false;
        String type = stack.getTag().getString("slot_type");
        return allowedType.equals(type);
    }
}
