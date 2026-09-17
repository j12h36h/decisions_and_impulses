package io.github.j12h36h.dai.client.title;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/**
 * Finds title-screen JSON without waiting for a world datapack reload.
 *
 * Built-in definitions are available from the mod classpath at the title
 * screen. The repository also early-scans local world datapacks so a pack can
 * contribute a title presentation before any world is opened, and it accepts
 * explicit global overrides from config/decisions_and_impulses/title_screens.
 */
public final class DAI_TitleScreenRepository {

    public static final String DIRECTORY = "dai_title_screens";
    private static final List<String> BUILTINS = List.of(
            "data/decisions_and_impulses/dai_title_screens/default.json",
            "data/decisions_and_impulses/dai_title_screens/minimal_example.json",
            "data/decisions_and_impulses/dai_title_screens/item_icon_showcase.json"
    );

    private static volatile DAI_TitleScreenDefinition cached;
    private static Map<String, JsonObject> serverDefinitions = Map.of();

    private DAI_TitleScreenRepository() {}

    public static DAI_TitleScreenDefinition current() {
        DAI_TitleScreenDefinition value = cached;
        if (value != null) return value;

        synchronized (DAI_TitleScreenRepository.class) {
            if (cached == null) {
                cached = reloadInternal();
            }
            return cached;
        }
    }

    public static DAI_TitleScreenDefinition reload() {
        synchronized (DAI_TitleScreenRepository.class) {
            cached = reloadInternal();
            return cached;
        }
    }

    /** Session-scoped title definitions supplied by the connected server datapack. */
    public static DAI_TitleScreenDefinition replaceServerDefinitions(Map<String, JsonObject> definitions) {
        synchronized (DAI_TitleScreenRepository.class) {
            Map<String, JsonObject> copy = new HashMap<>();
            if (definitions != null) definitions.forEach((id, json) -> {
                if (id != null && json != null) copy.put(id, json.deepCopy());
            });
            serverDefinitions = Map.copyOf(copy);
            cached = reloadInternal();
            return cached;
        }
    }

    private static void loadServerDefinitions(Map<String, DAI_TitleScreenDefinition> output) {
        serverDefinitions.forEach((id, json) -> register(output, id, json));
    }

    /**
     * Resolves the highest-priority title definition authored by one specific
     * Experience namespace without making that Experience globally active.
     *
     * This is used by DAI 4.1's Experience handoff: selecting a MAIN
     * Experience may enter its own title/menu loop first, while START/CONTINUE
     * buttons on that title still own the actual world launch. Installed MAIN
     * packs therefore do not hijack the global DAI shell until selected.
     */
    public static DAI_TitleScreenDefinition forExperience(String experienceId) {
        if (experienceId == null || experienceId.isBlank()) return null;
        String normalized = experienceId.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon <= 0) return null;
        String namespace = normalized.substring(0, colon);

        Map<String, DAI_TitleScreenDefinition> definitions = new HashMap<>();
        loadBuiltins(definitions);
        scanModDatapacks(definitions);
        scanWorldDatapacks(definitions);
        scanGlobalDatapacks(definitions);
        scanConfig(definitions);
        loadServerDefinitions(definitions);

        return definitions.values().stream()
                .filter(DAI_TitleScreenDefinition::enabled)
                .filter(definition -> authoredByNamespace(definition.id(), namespace))
                .max(Comparator
                        .comparingInt(DAI_TitleScreenDefinition::priority)
                        .thenComparing(DAI_TitleScreenDefinition::id))
                .orElse(null);
    }

    private static boolean authoredByNamespace(String definitionId, String namespace) {
        if (definitionId == null || namespace == null || namespace.isBlank()) return false;
        int provenance = definitionId.indexOf('@');
        String resourceId = provenance < 0 ? definitionId : definitionId.substring(0, provenance);
        int colon = resourceId.indexOf(':');
        if (colon <= 0) return false;
        return resourceId.substring(0, colon).equalsIgnoreCase(namespace);
    }

    private static DAI_TitleScreenDefinition reloadInternal() {
        Map<String, DAI_TitleScreenDefinition> definitions = new HashMap<>();

        loadBuiltins(definitions);
        scanModDatapacks(definitions);
        scanWorldDatapacks(definitions);
        scanGlobalDatapacks(definitions);
        scanConfig(definitions);
        loadServerDefinitions(definitions);

        String selectedNamespace = selectedExperienceNamespace();
        DAI_TitleScreenDefinition selected = definitions.values().stream()
                .filter(DAI_TitleScreenDefinition::enabled)
                .filter(definition -> allowedForCurrentExperience(definition.id(), selectedNamespace))
                .max(Comparator
                        .comparingInt(DAI_TitleScreenDefinition::priority)
                        .thenComparing(DAI_TitleScreenDefinition::id))
                .orElseGet(() -> DAI_TitleScreenDefinition.fallback(
                        "decisions_and_impulses:fallback"
                ));

        DAI_Core.LOGGER.info(
                "<DAI>: Selected title-screen definition '{}' from {} discovered definition(s).",
                selected.id(),
                definitions.size()
        );

        return selected;
    }


    /**
     * Installed experiences are selectable templates. Their early-scanned title
     * definitions must not become the application shell merely because the pack
     * exists in the global library or in another save. Once an experience is
     * explicitly pending/active, definitions from that experience namespace are
     * eligible again alongside built-in/config shell definitions.
     */
    private static String selectedExperienceNamespace() {
        DAI_ExperienceDefinition definition = DAI_ExperienceRuntime.active();
        if (definition == null) {
            DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();
            if (pending != null) definition = pending.definition();
        }
        if (definition == null || definition.id() == null) return "";

        String id = definition.id().trim();
        int colon = id.indexOf(':');
        return colon <= 0 ? "" : id.substring(0, colon);
    }

    private static boolean allowedForCurrentExperience(String definitionId, String selectedNamespace) {
        if (definitionId == null || definitionId.isBlank()) return false;

        // Built-in and explicit config shell definitions have no provenance
        // suffix and remain eligible regardless of the selected experience.
        int provenance = definitionId.indexOf('@');
        if (provenance < 0) return true;
        if (selectedNamespace == null || selectedNamespace.isBlank()) return false;

        String resourceId = definitionId.substring(0, provenance);
        int colon = resourceId.indexOf(':');
        if (colon <= 0) return false;
        return resourceId.substring(0, colon).equals(selectedNamespace);
    }

    private static void loadBuiltins(Map<String, DAI_TitleScreenDefinition> output) {
        ClassLoader loader = DAI_TitleScreenRepository.class.getClassLoader();
        for (String resource : BUILTINS) {
            try (InputStream stream = loader.getResourceAsStream(resource)) {
                if (stream == null) continue;
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    String file = resource.substring(resource.lastIndexOf('/') + 1);
                    String path = file.endsWith(".json")
                            ? file.substring(0, file.length() - 5)
                            : file;
                    register(output, "decisions_and_impulses:" + path, parsed.getAsJsonObject());
                }
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn("<DAI>: Failed to read built-in title screen '{}'.", resource, exception);
            }
        }
    }


    /** Early title discovery for MAIN experiences embedded directly in mod JARs. */
    private static void scanModDatapacks(Map<String, DAI_TitleScreenDefinition> output) {
        Path mods = gameDirectory().resolve("mods");
        if (!Files.isDirectory(mods)) return;

        try (Stream<Path> entries = Files.list(mods)) {
            for (Path mod : entries.filter(Files::isRegularFile).sorted().toList()) {
                String name = mod.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (!name.endsWith(".jar")) continue;
                if (!DAI_DatapackMetadata.isMain(mod)) continue;
                scanDatapackZip(output, "mod:" + mod.getFileName(), mod);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Failed to early-scan installed mod datapacks for title screens.",
                    exception
            );
        }
    }

    private static void scanWorldDatapacks(Map<String, DAI_TitleScreenDefinition> output) {
        Path game = gameDirectory();
        Path saves = game.resolve("saves");
        if (!Files.isDirectory(saves)) return;

        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(Files::isDirectory).sorted().toList()) {
                Path datapacks = world.resolve("datapacks");
                if (!Files.isDirectory(datapacks)) continue;

                try (Stream<Path> packs = Files.list(datapacks)) {
                    for (Path pack : packs.sorted().toList()) {
                        if (!DAI_DatapackMetadata.isMain(pack)) continue;
                        if (Files.isDirectory(pack)) {
                            scanDatapackDirectory(output, world.getFileName().toString(), pack);
                        } else if (pack.getFileName().toString().toLowerCase().endsWith(".zip")) {
                            scanDatapackZip(output, world.getFileName().toString(), pack);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to early-scan world datapacks for title screens.", exception);
        }
    }

    private static void scanGlobalDatapacks(Map<String, DAI_TitleScreenDefinition> output) {
        Path datapacks = DAI_GlobalDatapackLibrary.initialize();
        if (!Files.isDirectory(datapacks)) return;

        try (Stream<Path> packs = Files.list(datapacks)) {
            for (Path pack : packs.sorted().toList()) {
                if (!DAI_DatapackMetadata.isMain(pack)) continue;
                if (Files.isDirectory(pack)) {
                    scanDatapackDirectory(output, "global", pack);
                } else if (pack.getFileName().toString().toLowerCase().endsWith(".zip")) {
                    scanDatapackZip(output, "global", pack);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Failed to early-scan global datapack library for title screens.",
                    exception
            );
        }
    }

    private static void scanDatapackDirectory(
            Map<String, DAI_TitleScreenDefinition> output,
            String world,
            Path pack
    ) {
        try (Stream<Path> paths = Files.walk(pack)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = pack.relativize(path).toString().replace('\\', '/');
                ParsedResource resource = parseResourcePath(relative);
                if (resource == null) continue;
                JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    register(output, resource.id() + "@" + world, parsed.getAsJsonObject());
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to scan title screens from datapack '{}'.", pack, exception);
        }
    }

    private static void scanDatapackZip(
            Map<String, DAI_TitleScreenDefinition> output,
            String world,
            Path pack
    ) {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                ParsedResource resource = parseResourcePath(entry.getName());
                if (resource == null) continue;
                try (InputStream input = zip.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) {
                        register(output, resource.id() + "@" + world, parsed.getAsJsonObject());
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to scan title screens from zipped datapack '{}'.", pack, exception);
        }
    }

    private static ParsedResource parseResourcePath(String relative) {
        if (relative == null) return null;
        String normalized = relative.replace('\\', '/');
        if (!normalized.startsWith("data/") || !normalized.endsWith(".json")) return null;

        String[] parts = normalized.split("/");
        if (parts.length < 4 || !DIRECTORY.equals(parts[2])) return null;

        String namespace = parts[1];
        int prefix = ("data/" + namespace + "/" + DIRECTORY + "/").length();
        String path = normalized.substring(prefix, normalized.length() - 5);
        if (namespace.isBlank() || path.isBlank()) return null;
        return new ParsedResource(namespace + ":" + path);
    }

    private static Path gameDirectory() {
        Path config = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        return config.getParent() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : config.getParent();
    }

    private static void scanConfig(Map<String, DAI_TitleScreenDefinition> output) {
        Path root = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("decisions_and_impulses")
                .resolve("title_screens")
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(root)) return;

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> loadPath(output, root, path));
        } catch (IOException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to scan custom title screens in '{}'.", root, exception);
        }
    }

    private static void loadPath(
            Map<String, DAI_TitleScreenDefinition> output,
            Path root,
            Path path
    ) {
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return;

            String relative = root.relativize(path).toString().replace('\\', '/');
            if (relative.endsWith(".json")) {
                relative = relative.substring(0, relative.length() - 5);
            }
            register(output, "config:" + relative, parsed.getAsJsonObject());
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to parse title-screen JSON '{}'.", path, exception);
        }
    }

    private record ParsedResource(String id) {}

    private static void register(
            Map<String, DAI_TitleScreenDefinition> output,
            String id,
            JsonObject json
    ) {
        DAI_TitleScreenDefinition definition = DAI_TitleScreenDefinition.parse(id, json);
        output.put(id, definition);
    }
}
