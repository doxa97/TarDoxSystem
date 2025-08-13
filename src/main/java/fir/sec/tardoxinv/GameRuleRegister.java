package fir.sec.tardoxinv;

import net.minecraft.world.level.GameRules;

public class GameRuleRegister {

    // gamerule 참조용 Key
    public static GameRules.Key<GameRules.BooleanValue> USE_CUSTOM_INVENTORY;

    public static void register() {
        // 바닐라 방식으로 gamerule 등록
        USE_CUSTOM_INVENTORY = GameRules.register(
                "useCustomInventory",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true) // 기본값 true
        );
    }
}
