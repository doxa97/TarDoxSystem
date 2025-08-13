package fir.sec.tardoxinv.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class GridInventory {
    private final int width;
    private final int height;
    private final ItemStack[][] grid;

    public GridInventory(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new ItemStack[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                grid[x][y] = ItemStack.EMPTY;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /** 아이템 배치 (단순: 첫 좌표에 배치) */
    public boolean placeItem(ItemStack stack, int startX, int startY) {
        if (startX < width && startY < height && grid[startX][startY].isEmpty()) {
            grid[startX][startY] = stack;
            return true;
        }
        return false;
    }

    /** link_id로 아이템 검색 */
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

    /** link_id 아이템 제거 */
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

    /** NBT 저장 */
    public CompoundTag saveToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        // 간단: 좌표 저장
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!grid[x][y].isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    grid[x][y].save(itemTag);
                    tag.put(x + "," + y, itemTag);
                }
            }
        }
        return tag;
    }

    /** NBT 로드 */
    public void loadFromNBT(CompoundTag tag) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                String key = x + "," + y;
                if (tag.contains(key)) {
                    grid[x][y] = ItemStack.of(tag.getCompound(key));
                } else {
                    grid[x][y] = ItemStack.EMPTY;
                }
            }
        }
    }
}
