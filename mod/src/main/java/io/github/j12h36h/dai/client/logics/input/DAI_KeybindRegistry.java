package io.github.j12h36h.dai.client.logics.input;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DAI_KeybindRegistry {
    private static final Map<String, DAI_KeybindDefinition> DEFINITIONS = new LinkedHashMap<>();
    private DAI_KeybindRegistry() {}
    public static synchronized void clear() { DEFINITIONS.clear(); }
    public static synchronized void register(Identifier id, DAI_KeybindDefinition definition) {
        if (id != null && definition != null) DEFINITIONS.put(normalize(id.toString()), definition);
    }
    public static synchronized DAI_KeybindDefinition get(String id) { return DEFINITIONS.get(normalize(id)); }
    public static synchronized Map<String, DAI_KeybindDefinition> snapshot() { return Map.copyOf(DEFINITIONS); }
    public static synchronized int size() { return DEFINITIONS.size(); }
    private static String normalize(String id) { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }
}
