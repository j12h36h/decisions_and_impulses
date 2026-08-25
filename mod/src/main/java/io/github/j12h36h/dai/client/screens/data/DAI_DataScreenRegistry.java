package io.github.j12h36h.dai.client.screens.data;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_DataScreenRegistry {
    private static final Map<String, DAI_DataScreenDefinition> DEFINITIONS = new LinkedHashMap<>();
    private DAI_DataScreenRegistry() {}
    public static synchronized void clear() { DEFINITIONS.clear(); }
    public static synchronized void register(Identifier id, DAI_DataScreenDefinition definition) {
        if (id != null && definition != null) DEFINITIONS.put(id.toString(), definition);
    }
    public static synchronized DAI_DataScreenDefinition get(String id) {
        Identifier parsed = Identifier.tryParse(id == null ? "" : id.trim());
        return parsed == null ? null : DEFINITIONS.get(parsed.toString());
    }
    public static synchronized Map<String, DAI_DataScreenDefinition> snapshot() { return Map.copyOf(DEFINITIONS); }
}
