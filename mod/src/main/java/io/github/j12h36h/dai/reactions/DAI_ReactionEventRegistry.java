package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DAI_ReactionEventRegistry {

    public static final String PLAYER_ATTACK_ENTITY =
            "player_attack_entity";

    private static final Map<String, DAI_ReactionEventDefinition> EVENTS =
            new HashMap<>();

    private static final Set<String> BUILT_IN_EVENTS =
            new HashSet<>();

    private DAI_ReactionEventRegistry() {
        // Utility class.
    }

    public static void initialize() {

        EVENTS.clear();
        BUILT_IN_EVENTS.clear();

        registerBuiltIn(
                DAI_ReactionEventDefinition.allPhases(
                        PLAYER_ATTACK_ENTITY,
                        true,
                        true
                )
        );

        DAI_Core.debug(
                "<DAI>: Registered {} reaction event hook(s).",
                EVENTS.size()
        );
    }

    public static void register(
            DAI_ReactionEventDefinition definition
    ) {

        validateDefinition(
                definition
        );

        String id =
                normalize(
                        definition.id()
                );

        DAI_ReactionEventDefinition previous =
                EVENTS.put(
                        id,
                        definition.withId(id)
                );

        if (previous == null) {

            DAI_Core.debug(
                    "<DAI>: Registered reaction event '{}'.",
                    id
            );

        } else {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replaced reaction event '{}'.",
                    id
            );
        }
    }

    public static boolean isBuiltIn(
            String event
    ) {

        return BUILT_IN_EVENTS.contains(
                normalize(
                        event
                )
        );
    }

    public static void clearCustom() {

        EVENTS.keySet()
                .removeIf(id ->
                        !BUILT_IN_EVENTS.contains(id)
                );
    }

    public static DAI_ReactionEventDefinition get(
            String event
    ) {

        return EVENTS.get(
                normalize(
                        event
                )
        );
    }

    public static boolean contains(
            String event
    ) {

        return get(event) != null;
    }

    public static Set<String> ids() {
        return Set.copyOf(
                EVENTS.keySet()
        );
    }

    public static int size() {
        return EVENTS.size();
    }

    private static void registerBuiltIn(
            DAI_ReactionEventDefinition definition
    ) {

        validateDefinition(
                definition
        );

        String id =
                normalize(
                        definition.id()
                );

        EVENTS.put(
                id,
                definition.withId(id)
        );

        BUILT_IN_EVENTS.add(
                id
        );
    }

    private static void validateDefinition(
            DAI_ReactionEventDefinition definition
    ) {

        if (
                definition == null
                        || definition.id() == null
                        || definition.id().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Reaction event definition requires a non-blank id."
            );
        }
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
