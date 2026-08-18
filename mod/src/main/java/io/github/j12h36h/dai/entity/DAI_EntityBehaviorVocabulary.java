package io.github.j12h36h.dai.entity;

import java.util.Locale;
import java.util.Set;

/**
 * Server-owned action/condition vocabulary that is valid inside an entity
 * behavior_sequence.
 *
 * These nodes intentionally do not live in the normal client automation
 * registries: their actor is a Mob, not the local player. Keeping the names in
 * one common class lets the runtime and the validator agree without teaching
 * every datapack about Java-only implementation details.
 */
public final class DAI_EntityBehaviorVocabulary {

    private static final Set<String> ACTIONS = Set.of(
            "move_to",
            "approach",
            "follow",
            "follow_player",
            "move_to_target",
            "approach_target",
            "chase_target",
            "look_at",
            "look_at_player",
            "face_player",
            "look_at_target",
            "face_target",
            "stop",
            "stop_moving",
            "jump",
            "target_player",
            "acquire_player",
            "attack",
            "melee_attack",
            "attack_target",
            "clear_target",
            "flee_player",
            "avoid_player",
            "wander",
            "wait",
            "idle",
            "noop"
    );

    private static final Set<String> CONDITIONS = Set.of(
            "entity_health",
            "actor_health",
            "entity_health_percent",
            "actor_health_percent",
            "entity_age_ticks",
            "actor_age_ticks",
            "nearest_player_distance",
            "player_distance",
            "entity_has_target",
            "actor_has_target",
            "entity_target_alive",
            "actor_target_alive",
            "entity_target_distance",
            "actor_target_distance",
            "target_distance",
            "entity_can_see_target",
            "actor_can_see_target",
            "entity_on_ground",
            "actor_on_ground",
            "entity_in_water",
            "actor_in_water",
            "random_chance"
    );

    private DAI_EntityBehaviorVocabulary() {}

    public static boolean supportsAction(String raw) {
        return raw != null && ACTIONS.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean supportsCondition(String raw) {
        return raw != null && CONDITIONS.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static Set<String> actions() {
        return ACTIONS;
    }

    public static Set<String> conditions() {
        return CONDITIONS;
    }
}
