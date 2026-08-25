package io.github.j12h36h.dai.state;

import io.github.j12h36h.dai.api.DAI_StateStore;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DAI_StateRegistry {
    private static final Map<String, DAI_StateDefinition> DEFINITIONS = new LinkedHashMap<>();
    private DAI_StateRegistry() {}

    public static synchronized void clear() { DEFINITIONS.clear(); }

    public static synchronized void register(Identifier id, DAI_StateDefinition definition) {
        if (id != null && definition != null) DEFINITIONS.put(normalize(id.toString()), definition);
    }

    public static synchronized void register(String id, DAI_StateDefinition definition) {
        String key = normalize(id);
        if (!key.isBlank() && definition != null) DEFINITIONS.put(key, definition);
    }

    public static synchronized DAI_StateDefinition get(String id) {
        return DEFINITIONS.get(DAI_StateStore.normalizeKey(id));
    }

    public static synchronized Map<String, DAI_StateDefinition> snapshot() { return Map.copyOf(DEFINITIONS); }
    public static synchronized int size() { return DEFINITIONS.size(); }

    private static String normalize(String value) {
        return value == null ? "" : DAI_StateStore.normalizeKey(value).toLowerCase(Locale.ROOT);
    }
}
