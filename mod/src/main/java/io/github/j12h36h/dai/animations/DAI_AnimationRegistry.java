package io.github.j12h36h.dai.animations;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DAI_AnimationRegistry {
    private static final Map<String, DAI_AnimationDefinition> DEFINITIONS = new LinkedHashMap<>();
    private DAI_AnimationRegistry() {}

    public static void register(Identifier id, DAI_AnimationDefinition definition) {
        if (id != null && definition != null) DEFINITIONS.put(id.toString().toLowerCase(), definition);
    }
    public static DAI_AnimationDefinition get(String id) {
        return id == null ? null : DEFINITIONS.get(id.trim().toLowerCase());
    }
    public static boolean contains(String id) { return get(id) != null; }
    public static Set<String> ids() { return Set.copyOf(DEFINITIONS.keySet()); }
    public static int size() { return DEFINITIONS.size(); }
    public static void clear() { DEFINITIONS.clear(); }
}
