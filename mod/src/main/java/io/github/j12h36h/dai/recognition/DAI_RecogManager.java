package io.github.j12h36h.dai.recognition;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DAI_RecogManager {

    private static final Map<
                Identifier,
                DAI_RecogDefinition
                > DEFINITIONS =
            new HashMap<>();

    private DAI_RecogManager() {
        // Utility class.
    }

    public static void clear() {

        int removed =
                DEFINITIONS.size();

        DEFINITIONS.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} recognition definition(s).",
                removed
        );
    }

    public static void register(
            Identifier id,
            DAI_RecogDefinition definition
    ) {

        requireId(id);
        requireDefinition(definition);

        DAI_RecogDefinition previous =
                DEFINITIONS.put(
                        id,
                        definition
                );

        if (previous == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered recognition definition '{}'.",
                    id
            );

            return;
        }

        DAI_Core.LOGGER.warn(
                "<DAI>: Replaced existing recognition definition '{}'.",
                id
        );
    }

    public static DAI_RecogDefinition get(
            Identifier id
    ) {

        return DEFINITIONS.get(
                requireId(id)
        );
    }

    public static boolean contains(
            Identifier id
    ) {

        return DEFINITIONS.containsKey(
                requireId(id)
        );
    }

    public static Collection<Identifier> ids() {

        return Collections.unmodifiableSet(
                DEFINITIONS.keySet()
        );
    }

    public static Collection<DAI_RecogDefinition> definitions() {

        return Collections.unmodifiableCollection(
                DEFINITIONS.values()
        );
    }

    public static int size() {
        return DEFINITIONS.size();
    }

    private static Identifier requireId(
            Identifier id
    ) {

        if (id == null) {

            throw new IllegalArgumentException(
                    "Recognition definition id cannot be null."
            );
        }

        return id;
    }

    private static DAI_RecogDefinition requireDefinition(
            DAI_RecogDefinition definition
    ) {

        if (definition == null) {

            throw new IllegalArgumentException(
                    "Recognition definition cannot be null."
            );
        }

        return definition;
    }
}
