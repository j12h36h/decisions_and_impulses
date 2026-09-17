package io.github.j12h36h.dai.client.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.packs.DAI_PackInstallManager;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Discovers selectable DAI presentation profiles from local/included resource packs. */
public final class DAI_PresentationProfileService {

    private static final String SINGLE = "dai/presentation.json";
    private static final String MULTI = "dai/presentation_profiles.json";
    private static volatile List<Profile> cached;

    private DAI_PresentationProfileService() {}

    public static List<Profile> available() {
        List<Profile> value = cached;
        if (value != null) return value;
        return reload();
    }

    public static synchronized List<Profile> reload() {
        LinkedHashMap<String, Profile> profiles = new LinkedHashMap<>();
        register(profiles, Profile.DEFAULT);
        register(profiles, Profile.MINIMAL);

        scanResourcePackDirectory(profiles, DAI_PackInstallManager.gameDirectory().resolve("resourcepacks"), false);
        scanResourcePackDirectory(profiles, DAI_PackInstallManager.managedResourceRoot(), true);

        List<Profile> result = new ArrayList<>(profiles.values());
        result.sort(Comparator
                .comparing((Profile profile) -> !profile.builtin())
                .thenComparing(Profile::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Profile::id));
        cached = List.copyOf(result);
        return cached;
    }

    public static Profile selected() {
        String id = DAI_ClientConfig.presentationProfile();
        return available().stream()
                .filter(profile -> profile.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(Profile.DEFAULT);
    }

    private static void scanResourcePackDirectory(Map<String, Profile> output, Path root, boolean recursive) {
        if (!Files.isDirectory(root)) return;
        try {
            if (recursive) {
                try (var paths = Files.walk(root, 5)) {
                    for (Path path : paths.sorted().toList()) {
                        if (Files.isDirectory(path) && Files.isRegularFile(path.resolve("pack.mcmeta"))) {
                            readDirectory(output, path);
                        } else if (Files.isRegularFile(path) && zip(path)) {
                            readZip(output, path);
                        }
                    }
                }
            } else {
                try (var paths = Files.list(root)) {
                    for (Path path : paths.sorted().toList()) {
                        if (Files.isDirectory(path)) readDirectory(output, path);
                        else if (zip(path)) readZip(output, path);
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not scan presentation profiles under '{}'.", root, exception);
        }
    }

    private static void readDirectory(Map<String, Profile> output, Path pack) {
        readJsonFile(output, pack, pack.resolve(SINGLE));
        readJsonFile(output, pack, pack.resolve(MULTI));
    }

    private static void readJsonFile(Map<String, Profile> output, Path sourcePack, Path file) {
        if (!Files.isRegularFile(file)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            parseProfiles(output, sourcePack, parsed);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Invalid presentation profile file '{}'.", file, exception);
        }
    }

    private static void readZip(Map<String, Profile> output, Path pack) {
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            for (String name : List.of(SINGLE, MULTI)) {
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) continue;
                try (InputStream input = zip.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    parseProfiles(output, pack, JsonParser.parseReader(reader));
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not inspect presentation definitions in '{}'.", pack, exception);
        }
    }

    private static void parseProfiles(Map<String, Profile> output, Path sourcePack, JsonElement parsed) {
        if (parsed == null) return;
        if (parsed.isJsonArray()) {
            for (JsonElement value : parsed.getAsJsonArray()) parseProfiles(output, sourcePack, value);
            return;
        }
        if (!parsed.isJsonObject()) return;
        JsonObject root = parsed.getAsJsonObject();
        JsonArray profiles = array(root, "profiles");
        if (profiles != null) {
            for (JsonElement value : profiles) parseProfiles(output, sourcePack, value);
            return;
        }

        String id = string(root, "id", "");
        if (id.isBlank()) return;
        String name = string(root, "name", id);
        String mode = string(root, "mode", "universe").trim().toLowerCase(Locale.ROOT);
        JsonObject loading = object(root, "loading");
        JsonObject colors = object(root, "colors");
        if (colors == null) colors = loading == null ? null : object(loading, "colors");

        int top = color(colors, "background_top", 0xFF08050D);
        int bottom = color(colors, "background_bottom", 0xFF12091A);
        int primary = color(colors, "primary", 0xFFFF8428);
        int secondary = color(colors, "secondary", 0xFFA855F7);
        int text = color(colors, "text", 0xFFF7F0FF);
        String background = loading == null ? "" : string(loading, "background_texture", "");
        String logo = loading == null ? "" : string(loading, "logo_texture", "");
        int logoSize = loading == null ? 72 : integer(loading, "logo_size", 72);

        register(output, new Profile(
                id, name, mode, top, bottom, primary, secondary, text,
                background, logo, Math.max(16, Math.min(256, logoSize)),
                sourcePack, false
        ));
    }

    private static void register(Map<String, Profile> output, Profile profile) {
        String key = profile.id().toLowerCase(Locale.ROOT);
        Profile existing = output.get(key);
        if (existing != null && existing.builtin() && !profile.builtin()) {
            DAI_Core.LOGGER.warn("<DAI>: Presentation pack '{}' attempted to replace built-in profile '{}'; ignoring it.",
                    profile.sourcePack(), existing.id());
            return;
        }
        output.put(key, profile);
    }

    private static boolean zip(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root == null ? null : root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        if (root == null || !root.has(key)) return fallback;
        try { return root.get(key).getAsInt(); } catch (Exception ignored) { return fallback; }
    }

    private static int color(JsonObject root, String key, int fallback) {
        String value = string(root, key, "").trim();
        if (value.isBlank()) return fallback;
        try {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            if (hex.length() == 6) return (int) (0xFF000000L | Long.parseLong(hex, 16));
            if (hex.length() == 8) return (int) Long.parseLong(hex, 16);
        } catch (Exception ignored) { }
        return fallback;
    }

    public record Profile(
            String id,
            String name,
            String mode,
            int backgroundTop,
            int backgroundBottom,
            int primary,
            int secondary,
            int text,
            String backgroundTexture,
            String logoTexture,
            int logoSize,
            Path sourcePack,
            boolean builtin
    ) {
        public static final Profile DEFAULT = new Profile(
                "dai:default", "DAI Default", "universe",
                0xFF08050D, 0xFF12091A, 0xFFFF8428, 0xFFA855F7, 0xFFF7F0FF,
                "", "", 72, null, true
        );
        public static final Profile MINIMAL = new Profile(
                "dai:minimal", "DAI Minimal", "minimal",
                0xFF0E1114, 0xFF171C20, 0xFFE6E6E6, 0xFF818A91, 0xFFF4F4F4,
                "", "", 64, null, true
        );

        public Profile {
            id = id == null || id.isBlank() ? "dai:default" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
            mode = mode == null || mode.isBlank() ? "universe" : mode.trim().toLowerCase(Locale.ROOT);
            backgroundTexture = backgroundTexture == null ? "" : backgroundTexture.trim();
            logoTexture = logoTexture == null ? "" : logoTexture.trim();
        }

        public boolean minimal() { return "minimal".equals(mode); }
    }
}
