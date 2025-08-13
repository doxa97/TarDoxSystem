package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class BackpackInventory {
    private final int width;
    private final int height;
    private final ItemStack[][] grid;

    public BackpackInventory(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new ItemStack[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = ItemStack.EMPTY;
            }
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag items = new ListTag();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!grid[x][y].isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    grid[x][y].save(itemTag);
                    itemTag.putInt("X", x);
                    itemTag.putInt("Y", y);
                    items.add(itemTag);
                }
            }
        }

        tag.put("Items", items);
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        return tag;
    }

    public void loadFromNBT(CompoundTag tag) {
        ListTag items = tag.getList("Items", 10);
        for (Tag t : items) {
            CompoundTag itemTag = (CompoundTag) t;
            int x = itemTag.getInt("X");
            int y = itemTag.getInt("Y");
            grid[x][y] = ItemStack.of(itemTag);
        }
    }

    // 추가 메서드
    public boolean placeItem(ItemStack stack, int startX, int startY) {
        if (startX < width && startY < height && grid[startX][startY].isEmpty()) {
            grid[startX][startY] = stack;
            return true;
        }
        return false;
    }

    public ItemStack findByLinkId(UUID linkId) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                ItemStack stack = grid[x][y];
                if (!stack.isEmpty() && stack.hasTag() && stack.getTag().hasUUID("link_id") &&
                        stack.getTag().getUUID("link_id").equals(linkId)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public void removeByLinkId(UUID linkId) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                ItemStack stack = grid[x][y];
                if (!stack.isEmpty() && stack.hasTag() && stack.getTag().hasUUID("link_id") &&
                        stack.getTag().getUUID("link_id").equals(linkId)) {
                    grid[x][y] = ItemStack.EMPTY;
                    return;
                }
            }
        }
    }
}
