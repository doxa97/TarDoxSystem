package fir.sec.tardoxinv;

import net.minecraft.world.level.GameRules;

public class GameRuleRegister {

    public static GameRules.Key<GameRules.BooleanValue> USE_CUSTOM_INVENTORY;
    // 기능 6: 사망 시 동작
    public static GameRules.Key<GameRules.BooleanValue> KEEP_UTILITY_BINDINGS_ON_DEATH;
    public static GameRules.Key<GameRules.BooleanValue> DROP_BACKPACK_ON_DEATH;
    public static GameRules.Key<GameRules.BooleanValue> DROP_EQUIPMENT_ON_DEATH;

    public static void register() {
        USE_CUSTOM_INVENTORY = GameRules.register(
                "useCustomInventory",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true)
        );

        KEEP_UTILITY_BINDINGS_ON_DEATH = GameRules.register(
                "keepUtilityBindingsOnDeath",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true)
        );
        DROP_BACKPACK_ON_DEATH = GameRules.register(
                "dropBackpackOnDeath",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true)
        );
        DROP_EQUIPMENT_ON_DEATH = GameRules.register(
                "dropEquipmentOnDeath",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true)
        );
    }
}
