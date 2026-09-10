package io.github.j12h36h.dai.client.logics.action;

import io.github.j12h36h.dai.client.config.DAI_PlayerControls;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Config;

import java.util.Set;

/** Applies feature-module and player/experience permissions to DAI actions. */
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
            "attack_target", "attack_basic", "attack_start", "attack_stop",
            "skill_cast", "server_skill_cast"
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
            "structure_place", "feature_place",
            "fluid_apply", "fluid_remove"
    );

    private DAI_ActionPermissions() {}

    public static boolean allows(DAI_ActionDefinition action) {
        if (action == null) return false;

        String type = action.type();
        if (type == null || type.isBlank()) return true;

        if (!featureAllows(type)) return false;

        if ("automation_stop".equals(type)) return true;

        if (type.startsWith("automation_start_")
                || "automation_continue".equals(type)
                || "speedrun_find_portal_site".equals(type)) {
            return DAI_PlayerControls.automationEnabled();
        }

        if (!DAI_AutomationLogic.isActive()) {
            /* Direct experience-authored gameplay remains compatible, subject
             * only to the feature-module matrix above. */
            return true;
        }

        if (!DAI_PlayerControls.automationEnabled()) return false;
        if (MOVEMENT.contains(type) && !DAI_PlayerControls.automationMovement()) return false;
        if (COMBAT.contains(type) && !DAI_PlayerControls.automationCombat()) return false;
        if (WORLD_EDITING.contains(type) && !DAI_PlayerControls.automationWorldEditing()) return false;

        if ((type.startsWith("vertical_scaffold") || type.startsWith("wait_for_scaffold"))
                && (!DAI_PlayerControls.automationMovement()
                || !DAI_PlayerControls.automationWorldEditing())) {
            return false;
        }

        return true;
    }

    private static boolean featureAllows(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toLowerCase();

        if (type.startsWith("comiclife_")) return enabled("comic_life");
        if (type.startsWith("automation_") || type.startsWith("speedrun_")) return enabled("automation");
        if (MOVEMENT.contains(type)
                || type.startsWith("path_")
                || type.startsWith("waypoint_")
                || type.startsWith("explore_")
                || type.startsWith("approach_")) return enabled("navigation");
        if (COMBAT.contains(type) || type.startsWith("combat_")) return enabled("combat");
        if (WORLD_EDITING.contains(type)
                || type.startsWith("build_")
                || type.startsWith("break_")
                || type.startsWith("mine_")
                || type.startsWith("place_")) return enabled("world_editing");
        if (type.startsWith("overlay_")) return enabled("overlays");
        if (type.equals("screen_open") || type.equals("open_data_screen")) return enabled("data_screens");
        if (type.startsWith("animation_")) return enabled("animations");
        if (type.startsWith("cinematic_") || type.startsWith("eras_")) return enabled("cinematics");
        if (type.startsWith("learning_") || type.startsWith("companion_")) return enabled("learning");
        if (type.startsWith("customization_") || type.startsWith("game_customization_")) return enabled("customization");
        if (type.startsWith("physics_")) return enabled("physics");
        if (type.startsWith("vehicle_")) return enabled("vehicles");
        if (type.startsWith("projectile_") || type.startsWith("server_projectile_")) return enabled("projectiles");
        if (type.startsWith("effect_") || type.startsWith("server_effect_")
                || type.startsWith("potion_") || type.startsWith("server_potion_")) return enabled("effects");
        if (type.startsWith("audio_") || type.startsWith("sound_")) return enabled("audio");
        if (type.startsWith("fluid_")) return enabled("fluids");
        if (type.startsWith("portal_")) return enabled("portals");
        if (type.startsWith("worldgen_") || type.startsWith("structure_") || type.startsWith("feature_")) return enabled("worldgen");
        if (type.startsWith("state_")) return enabled("state");
        if (type.equals("emit_reaction_event") || type.startsWith("reaction_")) return enabled("reactions");
        if (type.startsWith("creative_")) return enabled("creative");
        if (type.startsWith("container_") || type.startsWith("inventory_")
                || type.startsWith("equipment_") || type.startsWith("craft")) return enabled("inventory");
        if (type.startsWith("interact") || type.startsWith("use_")) return enabled("interaction");
        if (type.startsWith("content_")) return enabled("content");

        return true;
    }

    private static boolean enabled(String module) {
        return DAI_Config.featureModuleEnabled(module);
    }
}
