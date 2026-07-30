package io.github.j12h36h.dai.core;

import java.util.List;

import io.github.j12h36h.dai.ui.DAI_Position;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TOGGLE_KEYBINDS = BUILDER
            .comment("Whether to disable vanilla keybinds")
            .define("toggleKeybinds", true);

    public static final ModConfigSpec.IntValue ACTION_DELAY = BUILDER
            .comment("Delay time between actions")
            .defineInRange("actionDelay", 300, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> UNIQUE_NICKNAME = BUILDER
            .comment("Unique Nickname (Client-Side Only)")
            .define("uniqueName", "???");

    public static final ModConfigSpec.ConfigValue<String> MENU_POSITION = BUILDER
            .comment("Menu buttons position")
            .define("menuPosition", DAI_Position.BOT_LEFT.toString());

    public static final ModConfigSpec.ConfigValue<String> DECISION_POSITION = BUILDER
            .comment("Decision menu position")
            .define("decisionPosition", DAI_Position.TOP_RIGHT.toString());

    public static final ModConfigSpec.ConfigValue<String> CHARACTER_POSITION = BUILDER
            .comment("Character menu position")
            .define("characterPosition", DAI_Position.TOP_LEFT.toString());

    public static final ModConfigSpec.ConfigValue<String> IMPULSE_POSITION = BUILDER
            .comment("Impulse menu position")
            .define("impulsePosition", DAI_Position.BOT_RIGHT.toString());

    static final ModConfigSpec SPEC = BUILDER.build();
}
