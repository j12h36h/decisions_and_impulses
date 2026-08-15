package io.github.j12h36h.dai.experience;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/** Small early-startup JSON scanner shared by experience and worldgen definitions. */
public final class DAI_EarlyJsonRepository {

    private DAI_EarlyJsonRepository() {}

    public static Map<String, JsonObject> scan(String dataDirectory, String configDirectory) {
        LinkedHashMap<String, JsonObject> result = new LinkedHashMap<>();
        scanWorldDatapacks(result, dataDirectory);
        scanGlobalDatapacks(result, dataDirectory);
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
        scanGlobalDatapacks(result, dataDirectory);
        scanConfig(result, configDirectory);
        return result;
    }

    private static void scanWorldDatapacks(Map<String, JsonObject> output, String directory) {
        Path saves = gameDirectory().resolve("saves");
        if (!Files.isDirectory(saves)) return;
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(Files::isDirectory).sorted().toList()) {
                scanDatapacks(output, world.resolve("datapacks"), directory);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to early-scan '{}' definitions from world datapacks.", directory, exception);
        }
    }

    private static void scanGlobalDatapacks(Map<String, JsonObject> output, String directory) {
        scanDatapacks(output, gameDirectory().resolve("datapacks"), directory);
    }

    private static void scanDatapacks(Map<String, JsonObject> output, Path datapacks, String directory) {
        if (!Files.isDirectory(datapacks)) return;
        try (Stream<Path> packs = Files.list(datapacks)) {
            for (Path pack : packs.sorted().toList()) {
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
