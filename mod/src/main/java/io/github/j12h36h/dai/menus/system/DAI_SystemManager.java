package io.github.j12h36h.dai.menus.system;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.DAI_MenuCategory;

import java.util.*;

public final class DAI_SystemManager {

    private static final EnumMap<
            DAI_MenuCategory,
            Map<String, DAI_SystemDefinition>
            > SYSTEMS =
            new EnumMap<>(DAI_MenuCategory.class);

    private static final Set<String> AVAILABLE_ACTION_MENUS =
            new HashSet<>();

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
        AVAILABLE_ACTION_MENUS.clear();

        DAI_Core.debug(
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

        if (category == DAI_MenuCategory.ACTION) {
            AVAILABLE_ACTION_MENUS.clear();
        }

        DAI_Core.debug(
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

        registerInternal(
                category,
                id,
                definition,
                false
        );
    }

    public static void registerAvailableAction(
            String id,
            DAI_SystemDefinition definition
    ) {

        registerInternal(
                DAI_MenuCategory.ACTION,
                id,
                definition,
                true
        );
    }

    public static boolean isAvailableActionMenu(
            String id
    ) {

        if (id == null || id.isBlank()) {
            return false;
        }

        return AVAILABLE_ACTION_MENUS.contains(
                id.trim()
        );
    }

    private static void registerInternal(
            DAI_MenuCategory category,
            String id,
            DAI_SystemDefinition definition,
            boolean availableAction
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

        if (category == DAI_MenuCategory.ACTION) {

            if (availableAction) {
                AVAILABLE_ACTION_MENUS.add(
                        normalizedId
                );
            } else {
                AVAILABLE_ACTION_MENUS.remove(
                        normalizedId
                );
            }
        }

        if (previous == null) {

            if (availableAction) {

                DAI_Core.debug(
                        "<DAI>: Registered dynamic available ACTION menu '{}'.",
                        normalizedId
                );

            } else {

                DAI_Core.debug(
                        "<DAI>: Registered {} system definition '{}'.",
                        category,
                        normalizedId
                );
            }

        } else {

            if (availableAction) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Replaced existing ACTION menu '{}' with a dynamic available definition.",
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