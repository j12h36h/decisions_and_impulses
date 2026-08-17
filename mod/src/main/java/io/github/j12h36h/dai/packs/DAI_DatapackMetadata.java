package io.github.j12h36h.dai.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/**
 * Reads the optional DAI block from pack.mcmeta and provides a backwards-
 * compatible role for older datapacks that predate role declarations.
 *
 * New packs may declare:
 *
 *   "dai": { "role": "main" }
 *   "dai": { "role": "addon" }
 *
 * Legacy inference intentionally keeps old packs working:
 * - a DAI pack containing dai_experiences or dai_title_screens is MAIN;
 * - another DAI pack is ADDON;
 * - a pack with no DAI content is UNMANAGED.
 */
public final class DAI_DatapackMetadata {

    private static final String PACK_META = "pack.mcmeta";
    private static final String EXPERIENCE_FOLDER = "/dai_experiences/";
    private static final String TITLE_FOLDER = "/dai_title_screens/";

    private DAI_DatapackMetadata() {}

    public static DAI_DatapackRole role(Path pack) {
        if (!isPackCandidate(pack)) return DAI_DatapackRole.UNMANAGED;

        String declared = declaredRole(pack);
        if (!declared.isBlank()) {
            DAI_DatapackRole parsed = parseDeclaredRole(declared);
            if (parsed != null) return parsed;

            DAI_Core.LOGGER.warn(
                    "<DAI>: Datapack '{}' declares unknown DAI role '{}'; falling back to legacy auto-detection.",
                    pack.getFileName(),
                    declared
            );
        }

        LegacyShape shape = legacyShape(pack);
        if (!shape.dai()) return DAI_DatapackRole.UNMANAGED;
        return shape.mainMarker() ? DAI_DatapackRole.MAIN : DAI_DatapackRole.ADDON;
    }

    public static boolean isMain(Path pack) {
        return role(pack) == DAI_DatapackRole.MAIN;
    }

    public static boolean isAddon(Path pack) {
        return role(pack) == DAI_DatapackRole.ADDON;
    }

    /** All globally installed DAI addons, deterministically ordered. */
    public static List<Path> globalAddons() {
        Path root = DAI_GlobalDatapackLibrary.initialize();
        if (!Files.isDirectory(root)) return List.of();

        List<Path> result = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                if (isAddon(entry)) result.add(entry.toAbsolutePath().normalize());
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not enumerate global DAI addon datapacks in '{}'.",
                    root,
                    exception
            );
        }
        return List.copyOf(result);
    }

    private static String declaredRole(Path pack) {
        try {
            JsonObject root = readPackMeta(pack);
            if (root == null || !root.has("dai") || !root.get("dai").isJsonObject()) return "";

            JsonObject dai = root.getAsJsonObject("dai");
            if (!dai.has("role") || !dai.get("role").isJsonPrimitive()) return "";
            return dai.get("role").getAsString().trim().toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not read DAI metadata from datapack '{}'.",
                    pack,
                    exception
            );
            return "";
        }
    }

    private static DAI_DatapackRole parseDeclaredRole(String role) {
        return switch (role) {
            case "main", "experience", "main_experience", "main-experience" -> DAI_DatapackRole.MAIN;
            case "addon", "add_on", "add-on", "extension", "module" -> DAI_DatapackRole.ADDON;
            case "unmanaged", "none", "vanilla" -> DAI_DatapackRole.UNMANAGED;
            default -> null;
        };
    }

    private static JsonObject readPackMeta(Path pack) throws Exception {
        if (Files.isDirectory(pack)) {
            Path meta = pack.resolve(PACK_META);
            if (!Files.isRegularFile(meta)) return null;
            JsonElement parsed = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry(PACK_META);
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream input = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        }
    }

    private static LegacyShape legacyShape(Path pack) {
        try {
            if (Files.isDirectory(pack)) return inspectDirectory(pack);
            return inspectZip(pack);
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not infer legacy DAI datapack role for '{}'.",
                    pack,
                    exception
            );
            return LegacyShape.NONE;
        }
    }

    private static LegacyShape inspectDirectory(Path pack) throws Exception {
        Path data = pack.resolve("data");
        if (!Files.isDirectory(data)) return LegacyShape.NONE;

        boolean dai = false;
        boolean main = false;
        try (Stream<Path> files = Files.walk(data)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String relative = "/data/" + data.relativize(path).toString().replace('\\', '/') + "/";
                if (isDaiResource(relative)) dai = true;
                if (isMainResource(relative)) main = true;
                if (dai && main) break;
            }
        }
        return new LegacyShape(dai, main);
    }

    private static LegacyShape inspectZip(Path pack) throws Exception {
        boolean dai = false;
        boolean main = false;
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = "/" + entry.getName().replace('\\', '/') + "/";
                if (isDaiResource(name)) dai = true;
                if (isMainResource(name)) main = true;
                if (dai && main) break;
            }
        }
        return new LegacyShape(dai, main);
    }

    private static boolean isDaiResource(String path) {
        if (path == null || !path.contains("/data/")) return false;
        // DAI's authored definition folders intentionally use a dai_ prefix.
        return path.contains("/dai_");
    }

    private static boolean isMainResource(String path) {
        return path != null
                && (path.contains(EXPERIENCE_FOLDER) || path.contains(TITLE_FOLDER));
    }

    private static boolean isPackCandidate(Path path) {
        if (path == null) return false;
        if (Files.isDirectory(path)) return Files.isRegularFile(path.resolve(PACK_META));
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private record LegacyShape(boolean dai, boolean mainMarker) {
        private static final LegacyShape NONE = new LegacyShape(false, false);
    }
}
