package io.github.j12h36h.dai.client.config;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Config;

/**
 * Resolves the effective player/creator controls for the active experience.
 *
 * Player preferences are always the outer permission boundary. An experience
 * may further restrict an autonomous capability or lower a performance cap,
 * but may never enable something the player disabled globally.
 */
public final class DAI_PlayerControls {

    private DAI_PlayerControls() {}

    public static boolean automationEnabled() {
        return DAI_Config.automationEnabled()
                && controls().automation();
    }

    public static boolean automationMovement() {
        return automationEnabled()
                && DAI_Config.automationMovement()
                && controls().automationMovement();
    }

    public static boolean automationCombat() {
        return automationEnabled()
                && DAI_Config.automationCombat()
                && controls().automationCombat();
    }

    public static boolean automationWorldEditing() {
        return automationEnabled()
                && DAI_Config.automationWorldEditing()
                && controls().automationWorldEditing();
    }

    public static int maxActionsPerSecond() {
        int player = Math.max(1, Math.min(20, DAI_Config.maxActionsPerSecond()));
        int creator = controls().maxActionsPerSecond();
        return creator > 0
                ? Math.max(1, Math.min(player, creator))
                : player;
    }

    public static int maxActionQueueSize() {
        int player = Math.max(16, Math.min(2048, DAI_Config.maxActionQueueSize()));
        int creator = controls().maxActionQueueSize();
        return creator > 0
                ? Math.max(16, Math.min(player, creator))
                : player;
    }

    private static DAI_ExperienceDefinition.Controls controls() {
        DAI_ExperienceDefinition active = DAI_ExperienceRuntime.active();
        return active == null
                ? DAI_ExperienceDefinition.Controls.DEFAULT
                : active.controls();
    }
}
