package io.github.j12h36h.dai.reactions;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DAI_ReactionEventRegistry {

    public static final String PLAYER_ATTACK_ENTITY =
            "player_attack_entity";

    private static final Map<String, DAI_ReactionEventDefinition> EVENTS =
            new HashMap<>();

    private DAI_ReactionEventRegistry() {
        // Utility class.
    }

    public static void initialize() {

        EVENTS.clear();

        register(
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

        if (
                definition == null
                        || definition.id() == null
                        || definition.id().isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Reaction event definition requires a non-blank id."
            );
        }

        String id =
                normalize(
                        definition.id()
                );

        EVENTS.put(
                id,
                definition
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

    public static int size() {
        return EVENTS.size();
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
