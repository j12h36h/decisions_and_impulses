package io.github.j12h36h.dai.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public class DAI_SystemManager {

    private static final EnumMap<DAI_MenuCategory, List<DAI_SystemDefinition>> SYSTEMS =
            new EnumMap<>(DAI_MenuCategory.class);

    static {
        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {
            SYSTEMS.put(category, new ArrayList<>());
        }
    }

    public static void clear() {
        SYSTEMS.values().forEach(List::clear);
    }

    public static void register(
            DAI_MenuCategory category,
            DAI_SystemDefinition definition
    ) {
        SYSTEMS.get(category).add(definition);
    }

    public static List<DAI_SystemDefinition> get(DAI_MenuCategory category) {
        return Collections.unmodifiableList(SYSTEMS.get(category));
    }
}