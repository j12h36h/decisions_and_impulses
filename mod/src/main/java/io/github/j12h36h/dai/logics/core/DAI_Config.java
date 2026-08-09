package io.github.j12h36h.dai.logics.core;

import io.github.j12h36h.dai.menus.DAI_Position;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class DAI_Config {

    public static final int CURRENT_CONFIG_VERSION = 1;

    private static final ModConfigSpec.Builder BUILDER =
            new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Integer> CONFIG_VERSION =
            BUILDER
                    .comment("DO NOT EDIT - Configuration file version")
                    .define(
                            "configVersion",
                            CURRENT_CONFIG_VERSION
                    );

    public static final ModConfigSpec.BooleanValue TOGGLE_KEYBINDS =
            BUILDER
                    .comment("Disable vanilla keybinds")
                    .define(
                            "toggleKeybinds",
                            false
                    );

    public static final ModConfigSpec.EnumValue<DAI_Position> SYSTEM_MENU_POSITION =
            BUILDER
                    .comment("System menu position")
                    .defineEnum(
                            "systemMenuPosition",
                            DAI_Position.BOT_LEFT
                    );

    public static final ModConfigSpec.EnumValue<DAI_Position> ACTION_MENU_POSITION =
            BUILDER
                    .comment("Action menu position")
                    .defineEnum(
                            "actionMenuPosition",
                            DAI_Position.BOT_RIGHT
                    );

    public static final ModConfigSpec SPEC =
            BUILDER.build();

    private DAI_Config() {
        // Utility class.
    }
}