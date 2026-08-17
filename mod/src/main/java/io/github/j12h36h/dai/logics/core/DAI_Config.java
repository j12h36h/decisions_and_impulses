package io.github.j12h36h.dai.logics.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * DAI's deliberately small set of player-facing framework preferences.
 *
 * Game/experience authors continue to define game rules in datapack JSON.
 * These options instead control how much autonomous control DAI may take,
 * performance ceilings, and presentation/pack convenience features.
 */
public final class DAI_Config {

    public static final int CURRENT_CONFIG_VERSION = 3;

    private static final ModConfigSpec.Builder BUILDER =
            new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<Integer> CONFIG_VERSION =
            BUILDER
                    .comment("DO NOT EDIT - Configuration file version")
                    .define(
                            "configVersion",
                            CURRENT_CONFIG_VERSION
                    );

    /*
     * Existing keys remain at their original paths so old user configs and
     * older DAI releases keep their choices unchanged.
     */
    public static final ModConfigSpec.BooleanValue TOGGLE_KEYBINDS =
            BUILDER
                    .comment("Disable vanilla keybinds while DAI owns input")
                    .define(
                            "toggleKeybinds",
                            false
                    );

    public static final ModConfigSpec.BooleanValue DEBUGGING =
            BUILDER
                    .comment(
                            "Enable DAI debugging and diagnostic telemetry. "
                                    + "When disabled, DAI debug messages, human trace output, "
                                    + "and logs/DAI_Log telemetry files are not produced."
                    )
                    .define(
                            "debugging",
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

    /*
     * Player automation permissions.
     * These only restrict DAI's autonomous player-control lifecycle. Direct
     * game logic authored by an experience remains available, so turning off
     * automation cannot accidentally disable a game's scripted cutscenes,
     * quests, world bootstrap, UI, or server functions.
     */
    public static final ModConfigSpec.BooleanValue AUTOMATION_ENABLED =
            BUILDER
                    .comment(
                            "Allow DAI autonomous gameplay modes. "
                                    + "Turning this off does not disable normal datapack-authored game logic."
                    )
                    .define(
                            "automationEnabled",
                            true
                    );

    public static final ModConfigSpec.BooleanValue AUTOMATION_MOVEMENT =
            BUILDER
                    .comment("Allow active DAI automation to move, look, pathfind, jump, swim, or fly for the player")
                    .define(
                            "automationMovement",
                            true
                    );

    public static final ModConfigSpec.BooleanValue AUTOMATION_COMBAT =
            BUILDER
                    .comment("Allow active DAI automation to perform player combat actions")
                    .define(
                            "automationCombat",
                            true
                    );

    public static final ModConfigSpec.BooleanValue AUTOMATION_WORLD_EDITING =
            BUILDER
                    .comment("Allow active DAI automation to mine, place, scaffold, or otherwise edit world blocks")
                    .define(
                            "automationWorldEditing",
                            true
                    );

    public static final ModConfigSpec.IntValue MAX_ACTIONS_PER_SECOND =
            BUILDER
                    .comment(
                            "Maximum number of NEW semantic DAI actions started per second. "
                                    + "Controllers already in progress still tick smoothly. "
                                    + "DAI may throttle below this limit when heap pressure is high."
                    )
                    .defineInRange(
                            "maxActionsPerSecond",
                            10,
                            1,
                            20
                    );

    public static final ModConfigSpec.IntValue MAX_ACTION_QUEUE_SIZE =
            BUILDER
                    .comment(
                            "Maximum queued DAI actions. Lower values bound memory/churn; "
                                    + "higher values permit larger authored sequences."
                    )
                    .defineInRange(
                            "maxActionQueueSize",
                            128,
                            16,
                            2048
                    );

    /* Pack convenience. */
    public static final ModConfigSpec.BooleanValue AUTO_ENABLE_ADDONS =
            BUILDER
                    .comment(
                            "Automatically include every discovered DAI ADDON datapack in DAI experiences and ordinary worlds. "
                                    + "Existing world datapack selections are preserved; disabling this stops automatic addon layering."
                    )
                    .define(
                            "autoEnableAddons",
                            true
                    );

    public static final ModConfigSpec.BooleanValue AUTO_ENABLE_MANAGED_RESOURCE_PACKS =
            BUILDER
                    .comment(
                            "Automatically register, enable, and persist resource packs installed through DAI's managed pack system. "
                                    + "Managed packs remain enabled after installs and updates without reopening Minecraft's Resource Packs screen."
                    )
                    .define(
                            "autoEnableManagedResourcePacks",
                            true
                    );

    /* Presentation/accessibility. */
    public static final ModConfigSpec.BooleanValue CUSTOM_TITLE_SCREENS =
            BUILDER
                    .comment("Allow datapacks/experiences to replace Minecraft's title screen with a DAI-authored title screen")
                    .define(
                            "customTitleScreens",
                            true
                    );

    public static final ModConfigSpec.DoubleValue OVERLAY_OPACITY =
            BUILDER
                    .comment(
                            "Global opacity multiplier for DAI HUD/overlay sprites. "
                                    + "A floor is kept so creator-authored interfaces remain usable."
                    )
                    .defineInRange(
                            "overlayOpacity",
                            1.0D,
                            0.25D,
                            1.0D
                    );

    public static final ModConfigSpec SPEC =
            BUILDER.build();

    public static boolean isDebuggingEnabled() {
        return bool(DEBUGGING, false);
    }

    public static boolean automationEnabled() {
        return bool(AUTOMATION_ENABLED, true);
    }

    public static boolean automationMovement() {
        return bool(AUTOMATION_MOVEMENT, true);
    }

    public static boolean automationCombat() {
        return bool(AUTOMATION_COMBAT, true);
    }

    public static boolean automationWorldEditing() {
        return bool(AUTOMATION_WORLD_EDITING, true);
    }

    public static int maxActionsPerSecond() {
        return integer(MAX_ACTIONS_PER_SECOND, 10);
    }

    public static int maxActionQueueSize() {
        return integer(MAX_ACTION_QUEUE_SIZE, 128);
    }

    public static boolean autoEnableAddons() {
        return bool(AUTO_ENABLE_ADDONS, true);
    }

    public static boolean autoEnableManagedResourcePacks() {
        return bool(AUTO_ENABLE_MANAGED_RESOURCE_PACKS, true);
    }

    public static boolean customTitleScreens() {
        return bool(CUSTOM_TITLE_SCREENS, true);
    }

    public static double overlayOpacity() {
        try {
            return OVERLAY_OPACITY.get();
        } catch (IllegalStateException exception) {
            return 1.0D;
        }
    }

    private static boolean bool(
            ModConfigSpec.BooleanValue value,
            boolean fallback
    ) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private static int integer(
            ModConfigSpec.IntValue value,
            int fallback
    ) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private DAI_Config() {
        // Utility class.
    }
}
