package io.github.j12h36h.dai.experience;

import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Early repository so title buttons can resolve experiences before a world is open. */
public final class DAI_ExperienceRepository {

    public static final String DIRECTORY = "dai_experiences";
    private static volatile Map<String, DAI_ExperienceDefinition> cached;

    private DAI_ExperienceRepository() {}

    public static DAI_ExperienceDefinition get(String id) {
        if (id == null || id.isBlank()) return null;
        String key = normalizeId(id);
        DAI_ExperienceDefinition direct = all().get(key);
        if (direct != null) return direct;

        // Convenience: permit an unqualified id when exactly one matching path exists.
        if (!key.contains(":")) {
            return all().values().stream()
                    .filter(value -> value.id().endsWith(":" + key))
                    .max(Comparator.comparingInt(DAI_ExperienceDefinition::priority))
                    .orElse(null);
        }
        return null;
    }

    public static Map<String, DAI_ExperienceDefinition> all() {
        Map<String, DAI_ExperienceDefinition> value = cached;
        if (value != null) return value;
        synchronized (DAI_ExperienceRepository.class) {
            if (cached == null) cached = reloadInternal();
            return cached;
        }
    }

    public static Map<String, DAI_ExperienceDefinition> reload() {
        synchronized (DAI_ExperienceRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    private static Map<String, DAI_ExperienceDefinition> reloadInternal() {
        LinkedHashMap<String, DAI_ExperienceDefinition> result = new LinkedHashMap<>();
        DAI_EarlyJsonRepository.scanMainPacks(DIRECTORY, "experiences").forEach((id, json) -> {
            try {
                DAI_ExperienceDefinition definition = DAI_ExperienceDefinition.parse(id, json);
                if (definition.enabled()) result.put(normalizeId(id), definition);
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn("<DAI>: Failed to parse experience definition '{}'.", id, exception);
            }
        });
        DAI_Core.LOGGER.info("<DAI>: Early-discovered {} experience definition(s).", result.size());
        return Map.copyOf(result);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
