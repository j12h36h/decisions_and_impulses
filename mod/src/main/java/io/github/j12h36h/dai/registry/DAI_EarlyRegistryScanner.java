package io.github.j12h36h.dai.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.content.DAI_ContentKind;
import io.github.j12h36h.dai.content.DAI_BlockSettings;
import io.github.j12h36h.dai.content.DAI_EffectSettings;
import io.github.j12h36h.dai.content.DAI_PotionSettings;
import io.github.j12h36h.dai.content.DAI_ParticleSettings;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Reads registry-backed DAI definitions directly from disk before Minecraft's
 * static registry events fire.
 *
 * This is intentionally independent of Minecraft's ResourceManager. At this
 * point no world has been selected and normal datapack loading has not begun.
 * The scanner therefore examines installed world datapacks, classpath/mod
 * resources, and zip datapacks itself and builds the union of native ids that
 * need registry shells for this JVM launch.
 */
public final class DAI_EarlyRegistryScanner {

    private static final Map<String, DAI_ContentKind> KINDS_BY_FOLDER = buildKinds();

    private DAI_EarlyRegistryScanner() {}

    public static ScanResult scan() {
        ScannerState state = new ScannerState();
        Path gameDir = DAI_GlobalDatapackLibrary.initialize().getParent();

        scanClasspath(state);
        scanInstalledMods(gameDir, state);

        // The global library is the source of truth for an installed/current
        // DAI pack. Scan it before save-local copies so a stale version that
        // DAI previously copied into a world cannot pin the JVM to an older
        // native entity/item/block shell on the next launch.
        scanGlobalDatapacks(state);
        scanInstalledWorlds(gameDir, state);

        return new ScanResult(
                Collections.unmodifiableMap(new LinkedHashMap<>(state.specs)),
                List.copyOf(state.conflicts),
                List.copyOf(state.sources)
        );
    }

    private static void scanClasspath(ScannerState state) {
        Set<String> visited = new LinkedHashSet<>();
        ClassLoader loader = DAI_EarlyRegistryScanner.class.getClassLoader();

        try {
            Enumeration<URL> roots = loader.getResources("data");
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                String external = url.toExternalForm();
                if (!visited.add(external)) continue;
                scanDataUrl(url, state);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Early registry scan could not enumerate classpath data roots.",
                    exception
            );
        }

        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank()) {
            for (String raw : classPath.split(java.io.File.pathSeparator)) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    Path entry = Path.of(raw).toAbsolutePath().normalize();
                    String key = entry.toString();
                    if (!visited.add(key)) continue;

                    if (Files.isDirectory(entry)) {
                        Path data = entry.resolve("data");
                        if (Files.isDirectory(data)) {
                            scanDataDirectory(data, "classpath:" + entry, state);
                        }
                    } else if (Files.isRegularFile(entry) && isArchive(entry)) {
                        scanArchive(entry, "classpath:" + entry.getFileName(), state);
                    }
                } catch (Exception ignored) {
                    // Best-effort discovery. World datapacks and the tombstone
                    // cache still provide deterministic fallback coverage.
                }
            }
        }
    }

    private static void scanDataUrl(URL url, ScannerState state) {
        try {
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                scanDataDirectory(
                        Path.of(url.toURI()),
                        "classpath:" + url,
                        state
                );
                return;
            }

            if ("jar".equalsIgnoreCase(url.getProtocol())) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    scanJarEntries(jar, "classpath:" + jar.getName(), state);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Early registry scan skipped classpath resource '{}'.",
                    url,
                    exception
            );
        }
    }

    private static void scanInstalledMods(Path gameDir, ScannerState state) {
        Path mods = gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) return;

        try (Stream<Path> entries = Files.list(mods)) {
            entries.filter(Files::isRegularFile)
                    .filter(DAI_EarlyRegistryScanner::isArchive)
                    .sorted()
                    .forEach(path -> scanArchive(path, "mod:" + path.getFileName(), state));
        } catch (IOException exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Early registry scan could not enumerate mod archives in '{}'.",
                    mods,
                    exception
            );
        }
    }

    private static void scanInstalledWorlds(Path gameDir, ScannerState state) {
        Path saves = gameDir.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (Stream<Path> worlds = Files.list(saves)) {
                worlds.filter(Files::isDirectory)
                        .sorted()
                        .map(world -> world.resolve("datapacks"))
                        .filter(Files::isDirectory)
                        .forEach(path -> scanDatapackDirectory(path, state));
            } catch (IOException exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Early registry scan could not enumerate '{}'.",
                        saves,
                        exception
                );
            }
        }

        // Dedicated-server and custom development layouts commonly keep a
        // world directly under the game directory rather than under saves/.
        try (Stream<Path> children = Files.isDirectory(gameDir)
                ? Files.list(gameDir)
                : Stream.empty()) {
            children.filter(Files::isDirectory)
                    .filter(path -> !path.equals(saves))
                    .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .map(path -> path.resolve("datapacks"))
                    .filter(Files::isDirectory)
                    .forEach(path -> scanDatapackDirectory(path, state));
        } catch (IOException ignored) {
            // Optional discovery path.
        }
    }

    private static void scanGlobalDatapacks(ScannerState state) {
        Path global = DAI_GlobalDatapackLibrary.initialize();
        if (Files.isDirectory(global)) {
            scanDatapackDirectory(global, state);
        }
    }

    private static void scanDatapackDirectory(Path datapacks, ScannerState state) {
        try (Stream<Path> entries = Files.list(datapacks)) {
            entries.sorted().forEach(entry -> {
                try {
                    if (Files.isDirectory(entry)) {
                        Path data = entry.resolve("data");
                        if (Files.isDirectory(data)) {
                            scanDataDirectory(data, entry.toString(), state);
                        }
                    } else if (Files.isRegularFile(entry) && isArchive(entry)) {
                        scanArchive(entry, entry.toString(), state);
                    }
                } catch (Exception exception) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Early registry scan skipped datapack '{}'.",
                            entry,
                            exception
                    );
                }
            });
        } catch (IOException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Early registry scan could not enumerate datapacks in '{}'.",
                    datapacks,
                    exception
            );
        }
    }

    private static void scanDataDirectory(
            Path dataRoot,
            String source,
            ScannerState state
    ) {
        try (Stream<Path> files = Files.walk(dataRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readDirectoryDefinition(dataRoot, path, source, state));
        } catch (IOException exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Early registry scan could not walk data root '{}'.",
                    dataRoot,
                    exception
            );
        }
    }

    private static void readDirectoryDefinition(
            Path dataRoot,
            Path file,
            String source,
            ScannerState state
    ) {
        Path relative = dataRoot.relativize(file);
        if (relative.getNameCount() < 3) return;

        String namespace = relative.getName(0).toString();
        String folder = relative.getName(1).toString();
        DAI_ContentKind kind = KINDS_BY_FOLDER.get(folder);
        if (kind == null) return;

        String contentPath = joinPath(relative.subpath(2, relative.getNameCount()));
        if (!contentPath.endsWith(".json")) return;
        contentPath = contentPath.substring(0, contentPath.length() - 5);

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            readDefinition(namespace, contentPath, kind, json, source, state);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Early registry scan could not read '{}'.",
                    file,
                    exception
            );
        }
    }

    private static void scanArchive(Path archive, String source, ScannerState state) {
        try (JarFile jar = new JarFile(archive.toFile())) {
            scanJarEntries(jar, source, state);
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Early registry scan skipped archive '{}'.",
                    archive,
                    exception
            );
        }
    }

    private static void scanJarEntries(JarFile jar, String source, ScannerState state) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;

            ArchivePath parsed = parseArchivePath(entry.getName());
            if (parsed == null) continue;

            try (var input = jar.getInputStream(entry)) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                readDefinition(
                        parsed.namespace,
                        parsed.contentPath,
                        parsed.kind,
                        json,
                        source,
                        state
                );
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Early registry scan could not read '{}!{}'.",
                        source,
                        entry.getName(),
                        exception
                );
            }
        }
    }

    private static ArchivePath parseArchivePath(String rawName) {
        String name = rawName.replace('\\', '/');
        if (!name.startsWith("data/") || !name.endsWith(".json")) return null;

        String[] parts = name.split("/");
        if (parts.length < 4) return null;

        DAI_ContentKind kind = KINDS_BY_FOLDER.get(parts[2]);
        if (kind == null) return null;

        StringBuilder contentPath = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) contentPath.append('/');
            contentPath.append(parts[i]);
        }
        String path = contentPath.toString();
        path = path.substring(0, path.length() - 5);
        return new ArchivePath(parts[1], path, kind);
    }

    private static void readDefinition(
            String namespace,
            String contentPath,
            DAI_ContentKind kind,
            String json,
            String source,
            ScannerState state
    ) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;
            JsonObject object = parsed.getAsJsonObject();
            if (!bool(object, "registry_backed", false)) return;

            String id = normalize(namespace) + ":" + normalizePath(contentPath);
            String nativeValue = string(object, "native_registry");
            DAI_RegistrySpec.NativeRegistry nativeRegistry =
                    DAI_RegistrySpec.NativeRegistry.parse(nativeValue, kind);
            if (nativeRegistry == null || id.startsWith(":")) return;

            JsonObject stats = object.has("stats") && object.get("stats").isJsonObject()
                    ? object.getAsJsonObject("stats")
                    : new JsonObject();

            String carrier = string(object, "carrier");
            String model = string(object, "model");
            if (model.isBlank()) model = carrier;

            JsonObject entity = object.has("entity") && object.get("entity").isJsonObject()
                    ? object.getAsJsonObject("entity")
                    : new JsonObject();

            String entityCategory = string(entity, "category");
            if (entityCategory.isBlank()) entityCategory = "creature";

            LinkedHashMap<String, Double> nativeAttributes = new LinkedHashMap<>();
            if (object.has("native_attributes") && object.get("native_attributes").isJsonObject()) {
                for (var attribute : object.getAsJsonObject("native_attributes").entrySet()) {
                    try {
                        nativeAttributes.put(attribute.getKey(), attribute.getValue().getAsDouble());
                    } catch (Exception ignored) {
                        // Malformed attribute entries are ignored here and diagnosed by normal reload validation.
                    }
                }
            }

            LinkedHashMap<String, String> nativeComponents = new LinkedHashMap<>();
            JsonObject components = null;
            if (object.has("native_components") && object.get("native_components").isJsonObject()) {
                components = object.getAsJsonObject("native_components");
            } else if (object.has("components") && object.get("components").isJsonObject()) {
                // `components` is the short/open API spelling. `native_components`
                // remains available when a pack wants to be explicit.
                components = object.getAsJsonObject("components");
            }
            if (components != null) {
                for (var component : components.entrySet()) {
                    if (component.getValue() == null || component.getValue().isJsonNull()) continue;
                    nativeComponents.put(
                            component.getKey().trim().toLowerCase(Locale.ROOT),
                            component.getValue().toString()
                    );
                }
            }

            DAI_RegistrySpec spec = new DAI_RegistrySpec(
                    id,
                    nativeRegistry,
                    kind.id(),
                    string(object, "display_name"),
                    model,
                    carrier,
                    integer(stats, "stack_size", 1),
                    integer(stats, "durability", 0),
                    blockSettings(object),
                    effectSettings(object),
                    potionSettings(object),
                    particleSettings(object),
                    entityCategory,
                    decimal(entity, "width", 0.6F),
                    decimal(entity, "height", 1.0F),
                    integer(entity, "tracking_range", 8),
                    integer(entity, "update_interval", 3),
                    bool(entity, "fire_immune", false),
                    bool(entity, "summonable", true),
                    bool(entity, "saveable", true),
                    nativeAttributes,
                    nativeComponents
            );

            state.accept(spec, source);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Early registry scan ignored malformed registry-backed definition '{}:{}' from '{}'.",
                    namespace,
                    contentPath,
                    source,
                    exception
            );
        }
    }



    private static DAI_EffectSettings effectSettings(JsonObject object) {
        JsonObject effect = object.has("effect") && object.get("effect").isJsonObject() ? object.getAsJsonObject("effect") : new JsonObject();
        return new DAI_EffectSettings(
                stringOr(effect, "category", "neutral"),
                integer(effect, "color", 0xFFFFFF),
                integer(effect, "tick_interval", 1)
        );
    }

    private static DAI_PotionSettings potionSettings(JsonObject object) {
        JsonObject potion = object.has("potion") && object.get("potion").isJsonObject() ? object.getAsJsonObject("potion") : new JsonObject();
        return new DAI_PotionSettings(stringList(potion, "effects"));
    }


    private static DAI_ParticleSettings particleSettings(JsonObject object) {
        JsonObject p = object.has("particle") && object.get("particle").isJsonObject()
                ? object.getAsJsonObject("particle") : new JsonObject();
        return new DAI_ParticleSettings(
                stringOr(p, "shape", "point"),
                integer(p, "count", 1),
                doubleValue(p, "spread_x", 0.0D),
                doubleValue(p, "spread_y", 0.0D),
                doubleValue(p, "spread_z", 0.0D),
                doubleValue(p, "speed", 0.0D),
                doubleValue(p, "radius", 1.0D),
                bool(p, "force", false),
                stringOr(p, "texture", "minecraft:generic"),
                integer(p, "lifetime", 20),
                doubleValue(p, "gravity", 0.0D),
                doubleValue(p, "friction", 0.98D),
                doubleValue(p, "scale", 1.0D),
                integer(p, "color", 0xFFFFFF),
                doubleValue(p, "alpha", 1.0D),
                bool(p, "full_bright", false),
                bool(p, "collision", false)
        );
    }

    private static DAI_BlockSettings blockSettings(JsonObject object) {
        JsonObject block = object.has("block") && object.get("block").isJsonObject()
                ? object.getAsJsonObject("block")
                : new JsonObject();

        return new DAI_BlockSettings(
                decimal(block, "hardness", 1.5F),
                decimal(block, "explosion_resistance", 6.0F),
                stringOr(block, "sound", "stone"),
                integer(block, "luminance", 0),
                decimal(block, "friction", 0.6F),
                decimal(block, "speed_factor", 1.0F),
                decimal(block, "jump_factor", 1.0F),
                bool(block, "requires_correct_tool", false),
                bool(block, "no_occlusion", false),
                bool(block, "no_collision", false),
                bool(block, "replaceable", false),
                bool(block, "random_ticks", false),
                bool(block, "ignited_by_lava", false),
                bool(block, "emissive_rendering", false),
                string(block, "map_color"),
                stringOr(block, "push_reaction", "normal"),
                stringList(block, "states"),
                doubleList(block, "outline_shape"),
                doubleList(block, "collision_shape"),
                integer(block, "redstone_signal", 0),
                bool(block, "climbable", false),
                string(block, "redstone_state"),
                string(block, "use_toggle_state"),
                integer(block, "scheduled_tick_delay", 0)
        );
    }

    private static java.util.List<String> stringList(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return java.util.List.of();
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (var element : object.getAsJsonArray(key)) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if (!value.isBlank()) values.add(value);
            }
        }
        return java.util.List.copyOf(values);
    }

    private static java.util.List<Double> doubleList(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return java.util.List.of();
        java.util.ArrayList<Double> values = new java.util.ArrayList<>();
        for (var element : object.getAsJsonArray(key)) {
            try {
                if (element != null && element.isJsonPrimitive()) values.add(element.getAsDouble());
            } catch (RuntimeException ignored) { }
        }
        return java.util.List.copyOf(values);
    }

    private static String stringOr(JsonObject object, String key, String fallback) {
        String value = string(object, key);
        return value.isBlank() ? fallback : value;
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static String joinPath(Path path) {
        StringBuilder result = new StringBuilder();
        for (Path part : path) {
            if (!result.isEmpty()) result.append('/');
            result.append(part);
        }
        return result.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(String value) {
        return normalize(value).replace('\\', '/');
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key)) return fallback;
        try { return object.get(key).getAsDouble(); } catch (Exception ignored) { return fallback; }
    }

    private static float decimal(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsFloat();
    }

    private static Map<String, DAI_ContentKind> buildKinds() {
        Map<String, DAI_ContentKind> result = new HashMap<>();
        for (DAI_ContentKind kind : DAI_ContentKind.values()) {
            result.put(kind.folder(), kind);
        }
        return Map.copyOf(result);
    }

    public record ScanResult(
            Map<String, DAI_RegistrySpec> specs,
            List<String> conflicts,
            List<String> sources
    ) {}

    private record ArchivePath(
            String namespace,
            String contentPath,
            DAI_ContentKind kind
    ) {}

    private static final class ScannerState {
        private final LinkedHashMap<String, DAI_RegistrySpec> specs = new LinkedHashMap<>();
        private final LinkedHashMap<String, String> sourceById = new LinkedHashMap<>();
        private final List<String> conflicts = new ArrayList<>();
        private final LinkedHashSet<String> sources = new LinkedHashSet<>();

        private void accept(DAI_RegistrySpec spec, String source) {
            if (spec == null || spec.identifier() == null) return;
            sources.add(source);

            DAI_RegistrySpec existing = findById(spec.id());
            if (existing == null) {
                specs.put(spec.key(), spec);
                sourceById.put(spec.id(), source);
                return;
            }

            if (existing.sameStaticDefinition(spec)) {
                return;
            }

            String firstSource = sourceById.getOrDefault(existing.id(), "<unknown>");
            boolean firstGlobal = isGlobalSource(firstSource);
            boolean incomingGlobal = isGlobalSource(source);

            // A globally installed pack is the update/source copy. Save-local
            // MAIN datapacks are managed mirrors and can legitimately be one
            // version behind at JVM bootstrap, before the experience stack has
            // had a chance to replace them. Prefer the global definition rather
            // than treating that expected stale mirror as a registry conflict.
            if (firstGlobal && !incomingGlobal) {
                DAI_Core.LOGGER.debug(
                        "<DAI>: Early registry scan ignored stale save-local definition '{}' from '{}' because current global source '{}' owns the id.",
                        spec.id(), source, firstSource
                );
                return;
            }

            if (incomingGlobal && !firstGlobal) {
                specs.entrySet().removeIf(entry -> entry.getValue().id().equals(spec.id()));
                specs.put(spec.key(), spec);
                sourceById.put(spec.id(), source);
                DAI_Core.LOGGER.info(
                        "<DAI>: Early registry scan selected current global definition '{}' over save-local/source copy '{}'.",
                        spec.id(), firstSource
                );
                return;
            }

            String conflict = "native id '" + spec.id()
                    + "' differs between '" + firstSource
                    + "' and '" + source + "'";
            if (!conflicts.contains(conflict)) {
                conflicts.add(conflict);
            }
        }

        private static boolean isGlobalSource(String source) {
            if (source == null || source.isBlank()) return false;
            try {
                return DAI_GlobalDatapackLibrary.contains(Path.of(source));
            } catch (Exception ignored) {
                return false;
            }
        }

        private DAI_RegistrySpec findById(String id) {
            for (DAI_RegistrySpec spec : specs.values()) {
                if (spec.id().equals(id)) return spec;
            }
            return null;
        }
    }
}
