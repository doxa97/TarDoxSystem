package fir.sec.tardoxinv.util;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** UUID 기반 핫바-커스텀 인벤 연동 유틸 */
public class LinkIdUtil {
    private static final String LINK_ID_KEY = "link_id";

    public static void ensureLinkId(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().hasUUID(LINK_ID_KEY)) {
            stack.getOrCreateTag().putUUID(LINK_ID_KEY, UUID.randomUUID());
        }
    }

    public static UUID getLinkId(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(LINK_ID_KEY)
                ? stack.getTag().getUUID(LINK_ID_KEY)
                : null;
    }
}
