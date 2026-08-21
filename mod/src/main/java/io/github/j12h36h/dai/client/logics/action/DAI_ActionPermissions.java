package io.github.j12h36h.dai.client.logics.action;

import io.github.j12h36h.dai.client.config.DAI_PlayerControls;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;

import java.util.Set;

/** Applies player/experience permissions only to DAI's autonomous lifecycle. */
public final class DAI_ActionPermissions {

    private static final Set<String> MOVEMENT = Set.of(
            "set_look", "add_look",
            "move", "jump",
            "crouch_toggle", "crouch_set",
            "sprint_toggle", "sprint_set",
            "swim_toggle", "swim_set",
            "approach_target_block", "wait_for_approach", "wait_for_target_block",
            "explore_for_block", "wait_for_exploration",
            "creative_flight_set", "creative_fly_to", "wait_for_creative_flight", "creative_hover"
    );

    private static final Set<String> COMBAT = Set.of(
            "attack_target", "attack_basic", "attack_start", "attack_stop"
    );

    private static final Set<String> WORLD_EDITING = Set.of(
            "break_targeted_once", "break_once", "break_start", "break_stop",
            "mine_targeted_block", "mine_nearest_block", "harvest_crop",
            "place", "place_targeted_block", "place_nearest_block", "place_block_at_selected_position",
            "exact_place_align", "exact_place_finish", "exact_place_verify",
            "vertical_scaffold_to_target", "wait_for_vertical_scaffold",
            "vertical_scaffold_descend", "wait_for_scaffold_descent",
            "creative_remove_block", "creative_place_block", "creative_set_block",
            "creative_build_blueprint", "creative_blueprint_cell", "wait_for_creative_build",
            "server_set_block", "server_break_block",
            "server_projectile_spawn", "projectile_spawn",
            "structure_place", "feature_place",
            "fluid_apply", "fluid_remove"
    );

    private DAI_ActionPermissions() {}

    public static boolean allows(DAI_ActionDefinition action) {
        if (action == null) return false;

        String type = action.type();
        if (type == null || type.isBlank()) return true;

        if ("automation_stop".equals(type)) {
            return true;
        }

        if (type.startsWith("automation_start_")
                || "automation_continue".equals(type)
                || "speedrun_find_portal_site".equals(type)) {
            return DAI_PlayerControls.automationEnabled();
        }

        if (!DAI_AutomationLogic.isActive()) {
            // Direct experience-authored gameplay is not treated as autonomous
            // player control and remains fully backwards compatible.
            return true;
        }

        if (!DAI_PlayerControls.automationEnabled()) {
            return false;
        }

        if (MOVEMENT.contains(type) && !DAI_PlayerControls.automationMovement()) {
            return false;
        }

        if (COMBAT.contains(type) && !DAI_PlayerControls.automationCombat()) {
            return false;
        }

        if (WORLD_EDITING.contains(type) && !DAI_PlayerControls.automationWorldEditing()) {
            return false;
        }

        // Scaffolding changes both movement and blocks, so either restriction
        // must stop it.
        if ((type.startsWith("vertical_scaffold") || type.startsWith("wait_for_scaffold"))
                && (!DAI_PlayerControls.automationMovement()
                || !DAI_PlayerControls.automationWorldEditing())) {
            return false;
        }

        return true;
    }
}
