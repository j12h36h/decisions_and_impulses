package io.github.j12h36h.dai.client.presentation.shell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.experience.DAI_EarlyJsonRepository;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
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

    /*
     * A full-takeover MAIN experience may own the application shell while it
     * is installed. "Return to DAI Universe" must still provide an explicit
     * escape hatch back to DAI's built-in shell for the remainder of the
     * current client session. The owner is remembered so selecting that exact
     * experience again can deliberately hand shell ownership back to it.
     */
    private static volatile boolean daiUniverseMode;
    private static volatile String suspendedOwnerExperienceId = "";

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

    /** True while the user has explicitly escaped an experience-owned shell. */
    public static boolean daiUniverseMode() {
        return daiUniverseMode;
    }

    /**
     * Temporarily suspends an experience-owned application shell and selects
     * DAI's built-in Universe shell. This is session-scoped and intentionally
     * does not mutate the datapack or user's installed-pack state.
     */
    public static boolean enterDaiUniverseMode() {
        synchronized (DAI_ShellPresentationRepository.class) {
            String owner = ownerExperienceId();
            suspendedOwnerExperienceId = owner == null ? "" : owner.trim();
            daiUniverseMode = true;
            cached = reloadInternal();
            DAI_Core.LOGGER.info(
                    "<DAI>: Entered DAI Universe mode; suspended shell owner='{}'.",
                    suspendedOwnerExperienceId.isBlank() ? "<none>" : suspendedOwnerExperienceId
            );
            return true;
        }
    }

    /**
     * Gives shell ownership back only when the user explicitly launches the
     * same Experience that was suspended by Return to DAI Universe.
     */
    public static boolean resumeForExperience(String experienceId) {
        synchronized (DAI_ShellPresentationRepository.class) {
            if (!daiUniverseMode) return false;

            String requested = experienceId == null ? "" : experienceId.trim();
            if (!suspendedOwnerExperienceId.isBlank()
                    && !suspendedOwnerExperienceId.equalsIgnoreCase(requested)) {
                return false;
            }

            daiUniverseMode = false;
            suspendedOwnerExperienceId = "";
            cached = reloadInternal();
            DAI_Core.LOGGER.info(
                    "<DAI>: Restored datapack shell ownership for Experience '{}'.",
                    requested.isBlank() ? "<unknown>" : requested
            );
            return true;
        }
    }

    /** Rolls back an attempted Universe transition that could not disconnect. */
    public static void cancelDaiUniverseMode() {
        synchronized (DAI_ShellPresentationRepository.class) {
            if (!daiUniverseMode) return;
            daiUniverseMode = false;
            suspendedOwnerExperienceId = "";
            cached = reloadInternal();
        }
    }

    /**
     * Experience that owns the currently selected application shell.
     *
     * Shell ownership is intentionally explicit when the JSON supplies
     * "experience". For backwards compatibility, a shell resource whose id
     * exactly matches an Experience id also owns that Experience. As a final
     * compatibility fallback, one unique/highest-priority Experience in the
     * shell's namespace is selected.
     */
    public static String ownerExperienceId() {
        DAI_ShellPresentationDefinition selected = current();
        if (selected == null) return "";

        String authored = selected.experience();
        if (authored != null && !authored.isBlank()) {
            DAI_ExperienceDefinition definition = DAI_ExperienceRepository.get(authored);
            return definition == null ? authored.trim() : definition.id();
        }

        String shellId = selected.id();
        DAI_ExperienceDefinition exact = DAI_ExperienceRepository.get(shellId);
        if (exact != null) return exact.id();

        String namespace = namespace(shellId);
        if (namespace.isBlank()) return "";

        return DAI_ExperienceRepository.all().values().stream()
                .filter(definition -> namespace.equals(namespace(definition.id())))
                .max(Comparator
                        .comparingInt(DAI_ExperienceDefinition::priority)
                        .thenComparing(DAI_ExperienceDefinition::id))
                .map(DAI_ExperienceDefinition::id)
                .orElse("");
    }

    /** Namespace whose early client-presentation data may participate in the shell. */
    public static String ownerNamespace() {
        return namespace(ownerExperienceId());
    }

    private static String namespace(String id) {
        if (id == null) return "";
        String value = id.trim();
        int colon = value.indexOf(':');
        return colon <= 0 ? "" : value.substring(0, colon);
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

        // Keep the engine-owned object before datapacks are merged. DAI
        // Universe mode is the explicit escape hatch from a full shell
        // takeover, so it must not be shadowed by a datapack redefining the
        // same resource id.
        DAI_ShellPresentationDefinition builtInUniverse =
                definitions.get("decisions_and_impulses:default");

        Map<String, JsonObject> external = DAI_EarlyJsonRepository.scanMainPacks(DIRECTORY, "shell_presentations");
        external.forEach((id, json) -> definitions.put(id, DAI_ShellPresentationDefinition.parse(id, json)));
        serverDefinitions.forEach((id, json) -> definitions.put(id, DAI_ShellPresentationDefinition.parse(id, json)));

        DAI_ShellPresentationDefinition selected;
        if (daiUniverseMode) {
            selected = builtInUniverse == null
                    ? DAI_ShellPresentationDefinition.defaultDefinition()
                    : builtInUniverse;
        } else {
            selected = definitions.values().stream()
                    .filter(DAI_ShellPresentationDefinition::enabled)
                    .max(Comparator.comparingInt(DAI_ShellPresentationDefinition::priority)
                            .thenComparing(DAI_ShellPresentationDefinition::id))
                    .orElseGet(DAI_ShellPresentationDefinition::defaultDefinition);
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Selected shell presentation '{}' from {} definition(s); owner experience='{}'.",
                selected.id(),
                definitions.size(),
                selected.experience()
        );
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
