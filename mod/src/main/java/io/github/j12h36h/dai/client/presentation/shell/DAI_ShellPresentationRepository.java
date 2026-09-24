package io.github.j12h36h.dai.client.presentation.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Engine-owned shell presentation repository for DAI 4.3.
 *
 * Experience/content packs no longer own pre-world navigation. Their authored
 * UI is loaded with the gameplay world instead. This repository therefore
 * selects only the built-in DAI launcher definition. The compatibility methods
 * remain so 4.2 callers compile while transitioning to world-runtime ownership.
 */
public final class DAI_ShellPresentationRepository {
    public static final String DIRECTORY = "dai_shell_presentations";
    private static final String BUILTIN = "data/decisions_and_impulses/dai_shell_presentations/default.json";
    private static volatile DAI_ShellPresentationDefinition cached;

    private DAI_ShellPresentationRepository() {}

    public static DAI_ShellPresentationDefinition current() {
        DAI_ShellPresentationDefinition value = cached;
        if (value != null) return value;
        synchronized (DAI_ShellPresentationRepository.class) {
            if (cached == null) cached = loadBuiltin();
            return cached;
        }
    }

    public static DAI_ShellPresentationDefinition reload() {
        synchronized (DAI_ShellPresentationRepository.class) {
            cached = loadBuiltin();
            return cached;
        }
    }

    /** DAI 4.3 always operates in the engine-owned launcher universe. */
    public static boolean daiUniverseMode() { return true; }

    /** Compatibility no-op: experiences can no longer own the launcher shell. */
    public static boolean enterDaiUniverseMode() { return false; }

    /** Compatibility no-op: world launch, not shell ownership, activates an experience. */
    public static boolean resumeForExperience(String experienceId) { return false; }

    public static void cancelDaiUniverseMode() { }

    /** No Experience owns DAI's global launcher in 4.3. */
    public static String ownerExperienceId() { return ""; }

    /** No Experience namespace is merged into pre-world shell data in 4.3. */
    public static String ownerNamespace() { return ""; }

    /**
     * Server-supplied shell definitions are deliberately ignored. Connected
     * worlds may still provide their own in-world screens and runtime data.
     */
    public static DAI_ShellPresentationDefinition replaceServerDefinitions(Map<String, JsonObject> definitions) {
        return reload();
    }

    private static DAI_ShellPresentationDefinition loadBuiltin() {
        try (InputStream stream = DAI_ShellPresentationRepository.class.getClassLoader().getResourceAsStream(BUILTIN)) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) {
                        DAI_ShellPresentationDefinition definition = DAI_ShellPresentationDefinition.parse(
                                "decisions_and_impulses:default",
                                parsed.getAsJsonObject()
                        );
                        DAI_Core.LOGGER.info("<DAI>: Selected engine-owned 4.3 shell presentation '{}'.", definition.id());
                        return definition;
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to load built-in shell presentation.", exception);
        }
        return DAI_ShellPresentationDefinition.defaultDefinition();
    }
}
