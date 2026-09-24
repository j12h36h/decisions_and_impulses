package io.github.j12h36h.dai.client.title;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-owned title-screen repository for DAI 4.3.
 *
 * Installed Experiences are world templates and may not replace DAI's global
 * title/navigation shell. Experience-specific presentation begins only after
 * the target gameplay world has been attached.
 */
public final class DAI_TitleScreenRepository {

    public static final String DIRECTORY = "dai_title_screens";
    private static final List<String> BUILTINS = List.of(
            "data/decisions_and_impulses/dai_title_screens/default.json"
    );

    private static volatile DAI_TitleScreenDefinition cached;

    private DAI_TitleScreenRepository() {}

    public static DAI_TitleScreenDefinition current() {
        DAI_TitleScreenDefinition value = cached;
        if (value != null) return value;
        synchronized (DAI_TitleScreenRepository.class) {
            if (cached == null) cached = reloadInternal();
            return cached;
        }
    }

    public static DAI_TitleScreenDefinition reload() {
        synchronized (DAI_TitleScreenRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    /** Server/world title overrides are intentionally ignored in 4.3. */
    public static DAI_TitleScreenDefinition replaceServerDefinitions(Map<String, JsonObject> definitions) {
        return reload();
    }

    /**
     * DAI 4.2 compatibility hook. Experiences no longer have a pre-world title
     * loop, so this always returns null.
     */
    public static DAI_TitleScreenDefinition forExperience(String experienceId) {
        return null;
    }

    private static DAI_TitleScreenDefinition reloadInternal() {
        Map<String, DAI_TitleScreenDefinition> definitions = new HashMap<>();
        ClassLoader loader = DAI_TitleScreenRepository.class.getClassLoader();

        for (String resource : BUILTINS) {
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream == null) continue;
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    String file = resource.substring(resource.lastIndexOf('/') + 1);
                    String path = file.endsWith(".json") ? file.substring(0, file.length() - 5) : file;
                    String id = "decisions_and_impulses:" + path;
                    definitions.put(id, DAI_TitleScreenDefinition.parse(id, parsed.getAsJsonObject()));
                }
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn("<DAI>: Failed to read built-in title screen '{}'.", resource, exception);
            }
        }

        DAI_TitleScreenDefinition selected = definitions.values().stream()
                .filter(DAI_TitleScreenDefinition::enabled)
                .max(Comparator.comparingInt(DAI_TitleScreenDefinition::priority)
                        .thenComparing(DAI_TitleScreenDefinition::id))
                .orElseGet(() -> DAI_TitleScreenDefinition.fallback("decisions_and_impulses:fallback"));

        DAI_Core.LOGGER.info("<DAI>: Selected engine-owned 4.3 title-screen definition '{}'.", selected.id());
        return selected;
    }
}
