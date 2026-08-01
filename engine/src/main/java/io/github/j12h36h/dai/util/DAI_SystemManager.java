package io.github.j12h36h.dai.util;

import io.github.j12h36h.dai.ui.DAI_MenuCategory;

import java.util.*;

public final class DAI_SystemManager {

    private static final EnumMap<DAI_MenuCategory, Map<String, DAI_SystemDefinition>> SYSTEMS =
            new EnumMap<>(DAI_MenuCategory.class);

    static {

        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {
            SYSTEMS.put(category, new HashMap<>());
        }
    }

    private DAI_SystemManager() {
        // Utility class.
    }

    public static void clear() {

        SYSTEMS.values().forEach(Map::clear);
    }

    public static void register(
            DAI_MenuCategory category,
            String id,
            DAI_SystemDefinition definition
    ) {

        SYSTEMS.get(category).put(id, definition);
    }

    public static DAI_SystemDefinition get(
            DAI_MenuCategory category,
            String id
    ) {

        return SYSTEMS.get(category).get(id);
    }

    public static Map<String, DAI_SystemDefinition> get(
            DAI_MenuCategory category
    ) {

        return Collections.unmodifiableMap(
                SYSTEMS.get(category)
        );
    }
}