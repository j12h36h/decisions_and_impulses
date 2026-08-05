package io.github.j12h36h.dai.system;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.ui.DAI_MenuCategory;

import java.util.*;

public final class DAI_SystemManager {

    private static final EnumMap<
            DAI_MenuCategory,
            Map<String, DAI_SystemDefinition>
            > SYSTEMS =
            new EnumMap<>(DAI_MenuCategory.class);

    static {

        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {
            SYSTEMS.put(
                    category,
                    new HashMap<>()
            );
        }
    }

    private DAI_SystemManager() {
        // Utility class.
    }

    public static void clear() {

        int removed = SYSTEMS.values()
                .stream()
                .mapToInt(Map::size)
                .sum();

        SYSTEMS.values().forEach(Map::clear);

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared all system definitions (removed {}).",
                removed
        );
    }

    public static void clear(
            DAI_MenuCategory category
    ) {

        Map<String, DAI_SystemDefinition> definitions =
                getCategoryMap(category);

        int removed = definitions.size();

        definitions.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} {} system definition(s).",
                removed,
                category
        );
    }

    public static void register(
            DAI_MenuCategory category,
            String id,
            DAI_SystemDefinition definition
    ) {

        if (id == null || id.isBlank()) {

            throw new IllegalArgumentException(
                    "System definition id cannot be null or blank."
            );
        }

        if (definition == null) {

            throw new IllegalArgumentException(
                    "System definition cannot be null."
            );
        }

        String normalizedId = id.trim();

        Map<String, DAI_SystemDefinition> definitions =
                getCategoryMap(category);

        DAI_SystemDefinition previous =
                definitions.put(
                        normalizedId,
                        definition
                );

        if (previous == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Registered {} system definition '{}'.",
                    category,
                    normalizedId
            );

        } else {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replaced existing {} system definition '{}'.",
                    category,
                    normalizedId
            );
        }
    }

    public static DAI_SystemDefinition get(
            DAI_MenuCategory category,
            String id
    ) {

        if (id == null || id.isBlank()) {
            return null;
        }

        return getCategoryMap(category)
                .get(id.trim());
    }

    public static Map<String, DAI_SystemDefinition> get(
            DAI_MenuCategory category
    ) {

        return Collections.unmodifiableMap(
                getCategoryMap(category)
        );
    }

    public static boolean contains(
            DAI_MenuCategory category,
            String id
    ) {

        if (id == null || id.isBlank()) {
            return false;
        }

        return getCategoryMap(category)
                .containsKey(id.trim());
    }

    public static int size(
            DAI_MenuCategory category
    ) {

        return getCategoryMap(category).size();
    }

    private static Map<String, DAI_SystemDefinition> getCategoryMap(
            DAI_MenuCategory category
    ) {

        if (category == null) {

            throw new IllegalArgumentException(
                    "System menu category cannot be null."
            );
        }

        Map<String, DAI_SystemDefinition> definitions =
                SYSTEMS.get(category);

        if (definitions == null) {

            throw new IllegalStateException(
                    "System category has not been initialized: "
                            + category
            );
        }

        return definitions;
    }
}