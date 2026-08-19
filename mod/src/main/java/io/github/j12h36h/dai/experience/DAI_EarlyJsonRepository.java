package io.github.j12h36h.dai.experience;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/** Small early-startup JSON scanner shared by experience and worldgen definitions. */
public final class DAI_EarlyJsonRepository {

    /** Installed mod archives are immutable for the lifetime of this JVM. */
    private static final Map<String, Map<String, JsonObject>> MOD_SCAN_CACHE = new ConcurrentHashMap<>();

    private DAI_EarlyJsonRepository() {}

    public static Map<String, JsonObject> scan(String dataDirectory, String configDirectory) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        scanModDatapacks(result, dataDirectory, false);
        scanWorldDatapacks(result, dataDirectory, false);
        scanGlobalDatapacks(result, dataDirectory, false);
        scanConfig(result, configDirectory);
        return result;
    }

    /**
     * Early scan restricted to datapacks that own a DAI main experience.
     * Explicit addons are excluded from experience/title ownership even when
     * they happen to ship similarly named definitions.
     */
    public static Map<String, JsonObject> scanMainPacks(String dataDirectory, String configDirectory) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        scanModDatapacks(result, dataDirectory, true);
        scanWorldDatapacks(result, dataDirectory, true);
        scanGlobalDatapacks(result, dataDirectory, true);
        scanConfig(result, configDirectory);
        return result;
    }

    /**
     * Client-local data scan used when no DAI server is present. World packs
     * are intentionally excluded so an unrelated save cannot leak automation
     * definitions into a multiplayer connection.
     */
    public static Map<String, JsonObject> scanClientData(String dataDirectory, String configDirectory) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        scanModDatapacks(result, dataDirectory, false);
        scanGlobalDatapacks(result, dataDirectory, false);
        scanConfig(result, configDirectory);
        return result;
    }


    /**
     * Mod JARs expose their data/<namespace> tree as a built-in server data
     * pack once Minecraft's ResourceManager exists. Early DAI systems run
     * before that point, so mirror the same discovery directly from /mods.
     * World/global datapacks are scanned afterwards and therefore retain
     * normal higher-precedence override behavior.
     */
    private static void scanModDatapacks(Map<String, JsonObject> output, String directory, boolean mainOnly) {
        String key = (directory == null ? "" : directory) + "|main=" + mainOnly;
        Map<String, JsonObject> cached = MOD_SCAN_CACHE.computeIfAbsent(
                key,
                ignored -> scanModDatapacksUncached(directory, mainOnly)
        );
        output.putAll(cached);
    }

    private static Map<String, JsonObject> scanModDatapacksUncached(String directory, boolean mainOnly) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        Path mods = gameDirectory().resolve("mods");
        if (!Files.isDirectory(mods)) return Map.of();

        try (Stream<Path> entries = Files.list(mods)) {
            for (Path mod : entries.filter(Files::isRegularFile).sorted().toList()) {
                String name = mod.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar")) continue;
                if (mainOnly && !DAI_DatapackMetadata.isMain(mod)) continue;
                scanZipPack(result, mod, directory);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Failed to early-scan '{}' definitions from installed mod datapacks.",
                    directory,
                    exception
            );
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static void scanWorldDatapacks(Map<String, JsonObject> output, String directory, boolean mainOnly) {
        Path saves = gameDirectory().resolve("saves");
        if (!Files.isDirectory(saves)) return;
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(Files::isDirectory).sorted().toList()) {
                scanDatapacks(output, world.resolve("datapacks"), directory, mainOnly);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to early-scan '{}' definitions from world datapacks.", directory, exception);
        }
    }

    private static void scanGlobalDatapacks(Map<String, JsonObject> output, String directory, boolean mainOnly) {
        scanDatapacks(output, DAI_GlobalDatapackLibrary.initialize(), directory, mainOnly);
    }

    private static void scanDatapacks(Map<String, JsonObject> output, Path datapacks, String directory, boolean mainOnly) {
        if (!Files.isDirectory(datapacks)) return;
        try (Stream<Path> packs = Files.list(datapacks)) {
            for (Path pack : packs.sorted().toList()) {
                if (mainOnly && !DAI_DatapackMetadata.isMain(pack)) continue;
                if (Files.isDirectory(pack)) {
                    scanDirectoryPack(output, pack, directory);
                } else if (pack.getFileName().toString().toLowerCase().endsWith(".zip")) {
                    scanZipPack(output, pack, directory);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not scan datapack directory '{}' for '{}'.", datapacks, directory, exception);
        }
    }

    private static void scanDirectoryPack(Map<String, JsonObject> output, Path pack, String directory) {
        Path data = pack.resolve("data");
        if (!Files.isDirectory(data)) return;
        try (Stream<Path> files = Files.walk(data)) {
            for (Path path : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String relative = pack.relativize(path).toString().replace('\\', '/');
                String id = resourceId(relative, directory);
                if (id == null) continue;
                JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) output.put(id, parsed.getAsJsonObject());
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not scan '{}' from '{}'.", directory, pack, exception);
        }
    }

    private static void scanZipPack(Map<String, JsonObject> output, Path pack, String directory) {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String id = resourceId(entry.getName(), directory);
                if (id == null) continue;
                try (InputStream input = zip.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) output.put(id, parsed.getAsJsonObject());
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not scan '{}' from zipped datapack '{}'.", directory, pack, exception);
        }
    }

    private static void scanConfig(Map<String, JsonObject> output, String configDirectory) {
        Path root = FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve(configDirectory)
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith(".json")) relative = relative.substring(0, relative.length() - 5);
                output.put("config:" + relative, parsed.getAsJsonObject());
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to scan config definitions in '{}'.", root, exception);
        }
    }

    private static String resourceId(String raw, String directory) {
        String name = raw.replace('\\', '/');
        if (!name.startsWith("data/") || !name.endsWith(".json")) return null;

        int namespaceEnd = name.indexOf('/', "data/".length());
        if (namespaceEnd < 0) return null;

        String namespace = name.substring("data/".length(), namespaceEnd);
        String prefix = "data/" + namespace + "/" + directory + "/";
        if (!name.startsWith(prefix) || name.length() <= prefix.length() + 5) return null;

        return namespace + ":" + name.substring(prefix.length(), name.length() - 5);
    }

    private static Path gameDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        return config.getParent() == null ? Path.of(".").toAbsolutePath().normalize() : config.getParent();
    }
}
