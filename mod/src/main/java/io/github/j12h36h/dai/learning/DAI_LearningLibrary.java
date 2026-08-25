package io.github.j12h36h.dai.learning;

import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DAI_LearningLibrary {
    private static final Map<Identifier, DAI_LearningAgentDefinition> AGENTS = new LinkedHashMap<>();

    private DAI_LearningLibrary() {}

    public static synchronized void clear() { AGENTS.clear(); }

    public static synchronized void register(Identifier id, DAI_LearningAgentDefinition definition) {
        if (id != null && definition != null) AGENTS.put(id, definition);
    }

    public static synchronized DAI_LearningAgentDefinition get(Identifier id) {
        return id == null ? null : AGENTS.get(id);
    }

    public static synchronized Map<Identifier, DAI_LearningAgentDefinition> agents() {
        return Map.copyOf(AGENTS);
    }

    public static synchronized Map.Entry<Identifier, DAI_LearningAgentDefinition> firstEnabled() {
        return AGENTS.entrySet().stream().filter(e -> e.getValue().enabled()).findFirst().orElse(null);
    }

    public static synchronized int size() { return AGENTS.size(); }
}
