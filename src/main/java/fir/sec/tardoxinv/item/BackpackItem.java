package fir.sec.tardoxinv.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;

public class BackpackItem extends Item {
    private final int width;
    private final int height;

    public BackpackItem(Properties properties, int width, int height) {
        super(properties.stacksTo(1));
        this.width = width;
        this.height = height;
    }

    private void ensureDefaults(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putString("slot_type", "backpack"); // 배낭 타입 명확화
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        ensureDefaults(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (!stack.hasTag()) ensureDefaults(stack);
    }
}
