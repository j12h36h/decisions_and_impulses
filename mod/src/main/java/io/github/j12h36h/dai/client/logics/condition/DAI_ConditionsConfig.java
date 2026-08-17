package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.config.DAI_PlayerControls;
import io.github.j12h36h.dai.logics.core.DAI_Config;

import java.util.Locale;

/** Read-only access to player/effective DAI preferences from datapack logic. */
public final class DAI_ConditionsConfig {

    private DAI_ConditionsConfig() {}

    public static void registerAll() {
        DAI_ConditionRegistry.register(
                "config_value",
                (context, condition) -> read(key(condition.target(), condition.stringValue()))
        );
    }

    private static DAI_ConditionValue read(String key) {
        return switch (key) {
            case "automation_enabled" -> DAI_ConditionValue.bool(DAI_PlayerControls.automationEnabled());
            case "automation_movement" -> DAI_ConditionValue.bool(DAI_PlayerControls.automationMovement());
            case "automation_combat" -> DAI_ConditionValue.bool(DAI_PlayerControls.automationCombat());
            case "automation_world_editing" -> DAI_ConditionValue.bool(DAI_PlayerControls.automationWorldEditing());
            case "max_actions_per_second" -> DAI_ConditionValue.number(DAI_PlayerControls.maxActionsPerSecond());
            case "max_action_queue_size" -> DAI_ConditionValue.number(DAI_PlayerControls.maxActionQueueSize());
            case "auto_enable_addons" -> DAI_ConditionValue.bool(DAI_Config.autoEnableAddons());
            case "auto_enable_managed_resource_packs" -> DAI_ConditionValue.bool(DAI_Config.autoEnableManagedResourcePacks());
            case "custom_title_screens" -> DAI_ConditionValue.bool(DAI_Config.customTitleScreens());
            case "overlay_opacity" -> DAI_ConditionValue.number(DAI_Config.overlayOpacity());
            case "debugging" -> DAI_ConditionValue.bool(DAI_Config.isDebuggingEnabled());
            default -> DAI_ConditionValue.missing();
        };
    }

    private static String key(String preferred, String fallback) {
        String value = preferred == null || preferred.isBlank() ? fallback : preferred;
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
