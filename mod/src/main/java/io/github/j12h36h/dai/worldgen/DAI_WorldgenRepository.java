package io.github.j12h36h.dai.worldgen;

import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Early repository for title-time world creation and experience launching. */
public final class DAI_WorldgenRepository {

    public static final String DIRECTORY = "dai_worldgen";
    private static volatile Map<String, DAI_WorldgenDefinition> cached;

    private DAI_WorldgenRepository() {}

    public static DAI_WorldgenDefinition get(String id) {
        if (id == null || id.isBlank()) return null;
        String key = normalize(id);
        DAI_WorldgenDefinition direct = all().get(key);
        if (direct != null) return direct;
        if (!key.contains(":")) {
            for (DAI_WorldgenDefinition definition : all().values()) {
                if (definition.id().endsWith(":" + key)) return definition;
            }
        }
        return null;
    }

    public static Map<String, DAI_WorldgenDefinition> all() {
        Map<String, DAI_WorldgenDefinition> value = cached;
        if (value != null) return value;
        synchronized (DAI_WorldgenRepository.class) {
            if (cached == null) cached = reloadInternal();
            return cached;
        }
    }

    public static Map<String, DAI_WorldgenDefinition> reload() {
        synchronized (DAI_WorldgenRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    private static Map<String, DAI_WorldgenDefinition> reloadInternal() {
        LinkedHashMap<String, DAI_WorldgenDefinition> result = new LinkedHashMap<>();
        DAI_EarlyJsonRepository.scan(DIRECTORY, "worldgen").forEach((id, json) -> {
            try {
                DAI_WorldgenDefinition definition = DAI_WorldgenDefinition.parse(id, json);
                if (definition.enabled()) result.put(normalize(id), definition);
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn("<DAI>: Failed to parse DAI worldgen definition '{}'.", id, exception);
            }
        });
        DAI_Core.LOGGER.info("<DAI>: Early-discovered {} DAI worldgen definition(s).", result.size());
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
