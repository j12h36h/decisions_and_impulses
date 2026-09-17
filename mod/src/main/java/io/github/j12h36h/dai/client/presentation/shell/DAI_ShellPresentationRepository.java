package io.github.j12h36h.dai.client.presentation.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Early-access shell routing repository, available before a world exists. */
public final class DAI_ShellPresentationRepository {
    public static final String DIRECTORY = "dai_shell_presentations";
    private static final String BUILTIN = "data/decisions_and_impulses/dai_shell_presentations/default.json";
    private static volatile DAI_ShellPresentationDefinition cached;
    private static Map<String, JsonObject> serverDefinitions = Map.of();

    private DAI_ShellPresentationRepository() {}

    public static DAI_ShellPresentationDefinition current() {
        DAI_ShellPresentationDefinition value = cached;
        if (value != null) return value;
        synchronized (DAI_ShellPresentationRepository.class) {
            if (cached == null) cached = reloadInternal();
            return cached;
        }
    }

    public static DAI_ShellPresentationDefinition reload() {
        synchronized (DAI_ShellPresentationRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    /** Session-scoped definitions supplied by the connected server datapack. */
    public static DAI_ShellPresentationDefinition replaceServerDefinitions(Map<String, JsonObject> definitions) {
        synchronized (DAI_ShellPresentationRepository.class) {
            LinkedHashMap<String, JsonObject> copy = new LinkedHashMap<>();
            if (definitions != null) definitions.forEach((id, json) -> {
                if (id != null && json != null) copy.put(id, json.deepCopy());
            });
            serverDefinitions = Map.copyOf(copy);
            cached = reloadInternal();
            return cached;
        }
    }

    private static DAI_ShellPresentationDefinition reloadInternal() {
        LinkedHashMap<String, DAI_ShellPresentationDefinition> definitions = new LinkedHashMap<>();
        loadBuiltin(definitions);
        Map<String, JsonObject> external = DAI_EarlyJsonRepository.scanMainPacks(DIRECTORY, "shell_presentations");
        external.forEach((id, json) -> definitions.put(id, DAI_ShellPresentationDefinition.parse(id, json)));
        serverDefinitions.forEach((id, json) -> definitions.put(id, DAI_ShellPresentationDefinition.parse(id, json)));

        DAI_ShellPresentationDefinition selected = definitions.values().stream()
                .filter(DAI_ShellPresentationDefinition::enabled)
                .max(Comparator.comparingInt(DAI_ShellPresentationDefinition::priority)
                        .thenComparing(DAI_ShellPresentationDefinition::id))
                .orElseGet(DAI_ShellPresentationDefinition::defaultDefinition);

        DAI_Core.LOGGER.info("<DAI>: Selected shell presentation '{}' from {} definition(s).",
                selected.id(), definitions.size());
        return selected;
    }

    private static void loadBuiltin(Map<String, DAI_ShellPresentationDefinition> output) {
        try (InputStream stream = DAI_ShellPresentationRepository.class.getClassLoader().getResourceAsStream(BUILTIN)) {
            if (stream == null) return;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) return;
                String id = "decisions_and_impulses:default";
                output.put(id, DAI_ShellPresentationDefinition.parse(id, parsed.getAsJsonObject()));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to load built-in shell presentation.", exception);
        }
    }
}
