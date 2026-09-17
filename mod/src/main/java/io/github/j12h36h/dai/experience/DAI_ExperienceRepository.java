package io.github.j12h36h.dai.experience;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Early repository so title buttons can resolve experiences before a world is open. */
public final class DAI_ExperienceRepository {

    public static final String DIRECTORY = "dai_experiences";
    private static volatile Map<String, DAI_ExperienceDefinition> cached;
    private static volatile Map<String, DAI_ExperienceDefinition> selectableCached;

    private DAI_ExperienceRepository() {}

    public static DAI_ExperienceDefinition get(String id) {
        return resolve(all(), id);
    }

    /**
     * Resolves only experiences installed at the game/modpack level. Existing
     * save-local MAIN packs are intentionally excluded from this view because
     * they belong to those saves, not to the New World template library.
     */
    public static DAI_ExperienceDefinition getSelectable(String id) {
        return resolve(selectable(), id);
    }

    public static Map<String, DAI_ExperienceDefinition> all() {
        Map<String, DAI_ExperienceDefinition> value = cached;
        if (value != null) return value;
        synchronized (DAI_ExperienceRepository.class) {
            if (cached == null) cached = reloadInternal();
            return cached;
        }
    }

    /** Installed/bundled experience templates suitable for creating new worlds. */
    public static Map<String, DAI_ExperienceDefinition> selectable() {
        Map<String, DAI_ExperienceDefinition> value = selectableCached;
        if (value != null) return value;
        synchronized (DAI_ExperienceRepository.class) {
            if (selectableCached == null) selectableCached = reloadSelectableInternal();
            return selectableCached;
        }
    }

    public static Map<String, DAI_ExperienceDefinition> reload() {
        synchronized (DAI_ExperienceRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    public static Map<String, DAI_ExperienceDefinition> reloadSelectable() {
        synchronized (DAI_ExperienceRepository.class) {
            selectableCached = reloadSelectableInternal();
            return selectableCached;
        }
    }

    /**
     * Returns the enabled experience definitions owned by one concrete pack.
     * The installer uses this to persist which selectable experiences belong
     * to each installed public Experience Pack.
     */
    public static Map<String, DAI_ExperienceDefinition> definitionsInPack(Path pack) {
        return parseDefinitions(DAI_EarlyJsonRepository.scanPack(pack, DIRECTORY), "pack " + pack);
    }

    private static Map<String, DAI_ExperienceDefinition> reloadInternal() {
        Map<String, DAI_ExperienceDefinition> result = parseDefinitions(
                DAI_EarlyJsonRepository.scanMainPacks(DIRECTORY, "experiences"),
                "all MAIN packs"
        );
        DAI_Core.LOGGER.info("<DAI>: Early-discovered {} experience definition(s).", result.size());
        return result;
    }

    private static Map<String, DAI_ExperienceDefinition> reloadSelectableInternal() {
        Map<String, DAI_ExperienceDefinition> result = parseDefinitions(
                DAI_EarlyJsonRepository.scanSelectableMainPacks(DIRECTORY),
                "installed/bundled MAIN packs"
        );
        DAI_Core.LOGGER.info(
                "<DAI>: {} installed/bundled experience definition(s) are selectable for new worlds.",
                result.size()
        );
        return result;
    }

    private static Map<String, DAI_ExperienceDefinition> parseDefinitions(
            Map<String, JsonObject> source,
            String sourceLabel
    ) {
        LinkedHashMap<String, DAI_ExperienceDefinition> result = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) return Map.of();

        source.forEach((id, json) -> {
            try {
                DAI_ExperienceDefinition definition = DAI_ExperienceDefinition.parse(id, json);
                if (definition.enabled()) result.put(normalizeId(id), definition);
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Failed to parse experience definition '{}' from {}.",
                        id,
                        sourceLabel,
                        exception
                );
            }
        });
        return Map.copyOf(result);
    }

    private static DAI_ExperienceDefinition resolve(
            Map<String, DAI_ExperienceDefinition> source,
            String id
    ) {
        if (id == null || id.isBlank() || source == null || source.isEmpty()) return null;
        String key = normalizeId(id);
        DAI_ExperienceDefinition direct = source.get(key);
        if (direct != null) return direct;

        // Convenience: permit an unqualified id when exactly one matching path exists.
        if (!key.contains(":")) {
            return source.values().stream()
                    .filter(value -> value.id().endsWith(":" + key))
                    .max(Comparator.comparingInt(DAI_ExperienceDefinition::priority))
                    .orElse(null);
        }
        return null;
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
