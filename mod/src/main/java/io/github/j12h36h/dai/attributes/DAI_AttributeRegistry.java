package io.github.j12h36h.dai.attributes;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DAI_AttributeRegistry {

    private static final Map<String, DAI_AttributeDefinition> DEFINITIONS = new LinkedHashMap<>();

    private DAI_AttributeRegistry() {}

    public static void register(Identifier id, DAI_AttributeDefinition definition) {
        if (id == null || definition == null) return;
        DEFINITIONS.put(id.toString().toLowerCase(), definition);
    }

    public static DAI_AttributeDefinition get(String id) {
        if (id == null) return null;
        return DEFINITIONS.get(id.trim().toLowerCase());
    }

    public static boolean contains(String id) {
        return get(id) != null;
    }

    public static Set<String> ids() {
        return Set.copyOf(DEFINITIONS.keySet());
    }

    public static int size() {
        return DEFINITIONS.size();
    }

    public static void clear() {
        int count = DEFINITIONS.size();
        DEFINITIONS.clear();
        DAI_Core.debug("<DAI>: Cleared {} custom attribute definition(s).", count);
    }
}
