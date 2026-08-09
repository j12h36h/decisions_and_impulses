package io.github.j12h36h.dai.objectives.recognition;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DAI_RecogRequirementRegistry {

    private static final Map<
            String,
            DAI_RecogRequirementHandler
            > HANDLERS =
            new HashMap<>();

    private DAI_RecogRequirementRegistry() {
        // Utility class.
    }

    public static void clear() {

        int removed =
                HANDLERS.size();

        HANDLERS.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} recognition requirement handler(s).",
                removed
        );
    }

    public static void register(
            String type,
            DAI_RecogRequirementHandler handler
    ) {

        String normalizedType =
                normalize(type);

        if (normalizedType.isEmpty()) {

            throw new IllegalArgumentException(
                    "Recognition requirement type cannot be null or blank."
            );
        }

        if (handler == null) {

            throw new IllegalArgumentException(
                    "Recognition requirement handler cannot be null."
            );
        }

        DAI_RecogRequirementHandler previous =
                HANDLERS.put(
                        normalizedType,
                        handler
                );

        if (previous == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered recognition requirement type '{}'.",
                    normalizedType
            );

            return;
        }

        DAI_Core.LOGGER.warn(
                "<DAI>: Replaced existing recognition requirement type '{}'.",
                normalizedType
        );
    }

    public static boolean evaluate(
            Level level,
            DAI_RecogSnapshot snapshot,
            Map<String, List<DAI_RecogBlock>> groups,
            DAI_RecogDefinition.DAI_RecogRequirement requirement
    ) {

        if (requirement == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot evaluate a null recognition requirement."
            );

            return false;
        }

        String type =
                normalize(
                        requirement.type()
                );

        DAI_RecogRequirementHandler handler =
                HANDLERS.get(type);

        if (handler == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown recognition requirement type '{}'.",
                    type
            );

            return false;
        }

        boolean result =
                handler.evaluate(
                        level,
                        snapshot,
                        groups,
                        requirement
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognition requirement '{}' = {}.",
                type,
                result
        );

        return result;
    }

    public static boolean contains(
            String type
    ) {

        return HANDLERS.containsKey(
                normalize(type)
        );
    }

    public static int size() {
        return HANDLERS.size();
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT);
    }
}
