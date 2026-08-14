package io.github.j12h36h.dai.content;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DAI_ContentRegistry {

    public record Entry(DAI_ContentKind kind, Identifier id, DAI_ContentDefinition definition) {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private DAI_ContentRegistry() {}

    public static void register(DAI_ContentKind kind, Identifier id, DAI_ContentDefinition definition) {
        if (kind == null || id == null || definition == null) return;
        ENTRIES.put(id.toString().toLowerCase(), new Entry(kind, id, definition));
    }

    public static Entry get(String id) {
        if (id == null) return null;
        return ENTRIES.get(id.trim().toLowerCase());
    }

    public static boolean contains(String id) {
        return get(id) != null;
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ENTRIES.keySet()));
    }

    public static Set<String> ids(DAI_ContentKind kind) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.kind() == kind) result.add(entry.id().toString());
        }
        return Collections.unmodifiableSet(result);
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static void clear(DAI_ContentKind kind) {
        if (kind == null) return;
        ENTRIES.entrySet().removeIf(entry -> entry.getValue().kind() == kind);
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
