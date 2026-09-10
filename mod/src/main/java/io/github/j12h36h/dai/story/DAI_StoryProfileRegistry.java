package io.github.j12h36h.dai.story;

import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of pack-defined story/archive profiles. */
public final class DAI_StoryProfileRegistry {
    private static final Map<Identifier, DAI_StoryProfileDefinition> VALUES = new LinkedHashMap<>();
    private DAI_StoryProfileRegistry() {}

    public static synchronized void clear() { VALUES.clear(); }
    public static synchronized void register(Identifier id, DAI_StoryProfileDefinition definition) {
        if (id != null && definition != null && definition.enabled()) VALUES.put(id, definition);
    }
    public static synchronized DAI_StoryProfileDefinition get(Identifier id) { return id == null ? null : VALUES.get(id); }
    public static synchronized DAI_StoryProfileDefinition get(String id) {
        Identifier key = Identifier.tryParse(id == null ? "" : id.trim());
        return key == null ? null : VALUES.get(key);
    }
    public static synchronized boolean isEmpty() { return VALUES.isEmpty(); }
    public static synchronized int size() { return VALUES.size(); }
    public static synchronized Map<Identifier, DAI_StoryProfileDefinition> snapshot() { return Map.copyOf(VALUES); }
    public static synchronized List<Map.Entry<Identifier, DAI_StoryProfileDefinition>> ordered() {
        return VALUES.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Identifier, DAI_StoryProfileDefinition>>comparingInt(e -> e.getValue().priority()).reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }
}
