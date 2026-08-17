package io.github.j12h36h.dai.client.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Auto-selects normal Minecraft resource packs that belong to DAI content.
 *
 * Official Packs use DAI's managed-resource repository and stable
 * dai_managed:* ids. Experience authors and modpacks, however, commonly put a
 * companion ZIP directly in <game>/resourcepacks. Vanilla persists those by
 * filename (file/Foo_v1.zip), so a renamed update silently drops out of the
 * enabled list. This class gives those packs a stable DAI-side identity and
 * rewrites only their vanilla file/... selection when the filename changes.
 *
 * A pack is considered a companion when either:
 *  - pack.mcmeta contains {"dai":{"auto_enable":true}}, or
 *  - one of its assets/<namespace> roots matches a namespace provided by a
 *    datapack in DAI's global <game>/datapacks library.
 *
 * If several files claim the same companion identity, the newest modified
 * file wins. This intentionally handles versioned filenames without enabling
 * both the old and new copy at once.
 */
public final class DAI_CompanionResourcePackPreferences {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RESOURCE_PACKS = "resourcePacks";
    private static final String INCOMPATIBLE_RESOURCE_PACKS = "incompatibleResourcePacks";

    private DAI_CompanionResourcePackPreferences() {}

    /**
     * Must be safe during client bootstrap, before Minecraft has finished
     * constructing its live Options object.
     */
    public static synchronized void reconcileSavedSelectionEarly() {
        if (!DAI_Config.autoEnableManagedResourcePacks()) return;

        try {
            Map<String, Companion> desiredByKey = discoverCompanions();
            Map<String, String> previousByKey = readState();

            LinkedHashSet<String> desiredIds = new LinkedHashSet<>();
            desiredByKey.values().stream()
                    .sorted(Comparator.comparing(Companion::key))
                    .forEach(companion -> desiredIds.add(companion.packId()));

            LinkedHashSet<String> previousIds =
                    new LinkedHashSet<>(previousByKey.values());

            Path options = gameDirectory().resolve("options.txt");
            List<String> lines = Files.isRegularFile(options)
                    ? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8))
                    : new ArrayList<>();

            Selection enabled = read(lines, RESOURCE_PACKS);
            Selection incompatible = read(lines, INCOMPATIBLE_RESOURCE_PACKS);
            if (!enabled.valid() || !incompatible.valid()) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: options.txt contains a malformed resource-pack list; companion auto-enable skipped."
                );
                return;
            }

            LinkedHashSet<String> nextEnabled = new LinkedHashSet<>(enabled.values());

            // Remove the old filename owned by each stable companion key.
            // Do not touch unrelated user-selected file/... packs.
            nextEnabled.removeAll(previousIds);
            nextEnabled.addAll(desiredIds);

            LinkedHashSet<String> nextIncompatible =
                    new LinkedHashSet<>(incompatible.values());
            nextIncompatible.removeAll(previousIds);
            nextIncompatible.removeAll(desiredIds);

            boolean changed = false;
            changed |= write(lines, RESOURCE_PACKS, enabled.index(), nextEnabled);
            changed |= write(
                    lines,
                    INCOMPATIBLE_RESOURCE_PACKS,
                    incompatible.index(),
                    nextIncompatible
            );

            if (changed) {
                Files.createDirectories(options.getParent());
                Path temp = options.resolveSibling(options.getFileName() + ".dai.tmp");
                Files.write(temp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(
                            temp,
                            options,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                } catch (Exception atomicMoveUnavailable) {
                    Files.move(temp, options, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            writeState(desiredByKey);

            if (!desiredIds.isEmpty()) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Auto-selected {} companion resource pack(s): {}.",
                        desiredIds.size(),
                        String.join(", ", desiredIds)
                );
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not reconcile companion resource-pack selection.",
                    exception
            );
        }
    }

    /** Keep the in-memory Options copy from restoring the stale filename. */
    public static void reconcileLiveSelection() {
        if (!DAI_Config.autoEnableManagedResourcePacks()) return;

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) return;

            minecraft.execute(() -> {
                try {
                    if (minecraft.options == null) return;

                    Map<String, Companion> desiredByKey = discoverCompanions();
                    Map<String, String> previousByKey = readState();

                    LinkedHashSet<String> desiredIds = new LinkedHashSet<>();
                    desiredByKey.values().stream()
                            .sorted(Comparator.comparing(Companion::key))
                            .forEach(companion -> desiredIds.add(companion.packId()));

                    LinkedHashSet<String> ownedIds =
                            new LinkedHashSet<>(previousByKey.values());
                    ownedIds.addAll(desiredIds);

                    LinkedHashSet<String> enabled =
                            new LinkedHashSet<>(minecraft.options.resourcePacks);
                    enabled.removeAll(ownedIds);
                    enabled.addAll(desiredIds);

                    LinkedHashSet<String> incompatible =
                            new LinkedHashSet<>(minecraft.options.incompatibleResourcePacks);
                    incompatible.removeAll(ownedIds);

                    List<String> nextEnabled = new ArrayList<>(enabled);
                    List<String> nextIncompatible = new ArrayList<>(incompatible);
                    if (nextEnabled.equals(minecraft.options.resourcePacks)
                            && nextIncompatible.equals(
                            minecraft.options.incompatibleResourcePacks
                    )) {
                        return;
                    }

                    minecraft.options.resourcePacks = nextEnabled;
                    minecraft.options.incompatibleResourcePacks = nextIncompatible;
                    minecraft.options.save();
                    writeState(desiredByKey);

                    if (!desiredIds.isEmpty()) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Persisted {} companion resource pack(s) into live Minecraft options.",
                                desiredIds.size()
                        );
                    }
                } catch (Exception exception) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not reconcile live companion resource-pack selection.",
                            exception
                    );
                }
            });
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not schedule companion resource-pack reconciliation.",
                    exception
            );
        }
    }

    private static Map<String, Companion> discoverCompanions() {
        Set<String> daiNamespaces = discoverGlobalDatapackNamespaces();
        Path root = gameDirectory().resolve("resourcepacks");
        if (!Files.isDirectory(root)) return Map.of();

        Map<String, Companion> chosen = new HashMap<>();
        try (var entries = Files.list(root)) {
            for (Path path : entries.toList()) {
                Candidate candidate = inspectResourcePack(path, daiNamespaces);
                if (candidate == null) continue;

                Companion next = new Companion(
                        candidate.key(),
                        "file/" + path.getFileName(),
                        path,
                        modified(path)
                );

                Companion old = chosen.get(next.key());
                if (old == null || next.modified() >= old.modified()) {
                    chosen.put(next.key(), next);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not scan resourcepacks directory for DAI companions.",
                    exception
            );
        }

        return new LinkedHashMap<>(chosen);
    }

    private static Candidate inspectResourcePack(
            Path path,
            Set<String> daiNamespaces
    ) {
        try {
            if (Files.isDirectory(path)) {
                Path metadataPath = path.resolve("pack.mcmeta");
                if (!Files.isRegularFile(metadataPath)) return null;

                JsonObject metadata = parseObject(Files.readString(
                        metadataPath,
                        StandardCharsets.UTF_8
                ));
                Set<String> assets = directoryNamespaces(path.resolve("assets"));
                return candidate(metadata, assets, daiNamespaces, path.getFileName().toString());
            }

            if (!Files.isRegularFile(path)
                    || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                return null;
            }

            try (ZipFile zip = new ZipFile(path.toFile())) {
                ZipEntry mcmeta = zip.getEntry("pack.mcmeta");
                if (mcmeta == null) return null;

                JsonObject metadata;
                try (Reader reader = new InputStreamReader(
                        zip.getInputStream(mcmeta),
                        StandardCharsets.UTF_8
                )) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    metadata = parsed != null && parsed.isJsonObject()
                            ? parsed.getAsJsonObject()
                            : new JsonObject();
                }

                Set<String> assets = zipNamespaces(zip, "assets/");
                return candidate(metadata, assets, daiNamespaces, path.getFileName().toString());
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Resource pack '{}' was not usable as a DAI companion: {}",
                    path,
                    exception.getMessage()
            );
            return null;
        }
    }

    private static Candidate candidate(
            JsonObject metadata,
            Set<String> assetNamespaces,
            Set<String> daiNamespaces,
            String fallbackName
    ) {
        JsonObject dai = metadata != null && metadata.has("dai")
                && metadata.get("dai").isJsonObject()
                ? metadata.getAsJsonObject("dai")
                : null;

        boolean explicit = bool(dai, "auto_enable", false)
                || bool(dai, "autoEnable", false);
        String explicitId = text(dai, "companion_id", "");
        if (explicitId.isBlank()) explicitId = text(dai, "companionId", "");

        List<String> matches = assetNamespaces.stream()
                .filter(daiNamespaces::contains)
                .sorted()
                .toList();

        if (!explicit && matches.isEmpty()) return null;

        String key;
        if (!explicitId.isBlank()) {
            key = "explicit:" + normalizeKey(explicitId);
        } else if (!matches.isEmpty()) {
            key = "namespace:" + String.join("+", matches);
        } else {
            key = "file:" + normalizeKey(fallbackName);
        }

        return new Candidate(key);
    }

    private static Set<String> discoverGlobalDatapackNamespaces() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Path root = DAI_GlobalDatapackLibrary.initialize();
        if (!Files.isDirectory(root)) return result;

        try (var entries = Files.list(root)) {
            for (Path path : entries.toList()) {
                if (Files.isDirectory(path)) {
                    result.addAll(directoryNamespaces(path.resolve("data")));
                    continue;
                }
                if (Files.isRegularFile(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(path.toFile())) {
                        result.addAll(zipNamespaces(zip, "data/"));
                    } catch (Exception ignored) {
                        // A broken/non-datapack ZIP is simply not a namespace source.
                    }
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not scan global datapacks for companion namespaces.",
                    exception
            );
        }

        result.remove("minecraft");
        return result;
    }

    private static Set<String> directoryNamespaces(Path root) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!Files.isDirectory(root)) return result;
        try (var entries = Files.list(root)) {
            for (Path path : entries.filter(Files::isDirectory).toList()) {
                String name = path.getFileName().toString().trim().toLowerCase(Locale.ROOT);
                if (!name.isBlank()) result.add(name);
            }
        } catch (Exception ignored) {
            // Empty set is safe.
        }
        return result;
    }

    private static Set<String> zipNamespaces(ZipFile zip, String prefix) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(prefix) || name.length() <= prefix.length()) continue;
            String remaining = name.substring(prefix.length());
            int slash = remaining.indexOf('/');
            if (slash <= 0) continue;
            String namespace = remaining.substring(0, slash)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!namespace.isBlank()) result.add(namespace);
        }
        return result;
    }

    private static Map<String, String> readState() {
        Path path = statePath();
        if (!Files.isRegularFile(path)) return Map.of();
        try {
            JsonObject root = parseObject(Files.readString(path, StandardCharsets.UTF_8));
            JsonObject entries = root.getAsJsonObject("companions");
            if (entries == null) return Map.of();

            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (String key : entries.keySet()) {
                try {
                    result.put(key, entries.get(key).getAsString());
                } catch (Exception ignored) {}
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static void writeState(Map<String, Companion> companions) {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("format", 1);
            JsonObject entries = new JsonObject();
            companions.values().stream()
                    .sorted(Comparator.comparing(Companion::key))
                    .forEach(companion -> entries.addProperty(
                            companion.key(),
                            companion.packId()
                    ));
            root.add("companions", entries);

            Path path = statePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not save companion resource-pack identity state.",
                    exception
            );
        }
    }

    private static Selection read(List<String> lines, String key) {
        String prefix = key + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(prefix)) continue;
            try {
                JsonElement parsed = JsonParser.parseString(
                        line.substring(prefix.length()).trim()
                );
                if (!parsed.isJsonArray()) return new Selection(i, List.of(), false);
                List<String> values = new ArrayList<>();
                for (JsonElement element : parsed.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive()) {
                        values.add(element.getAsString());
                    }
                }
                return new Selection(i, values, true);
            } catch (Exception ignored) {
                return new Selection(i, List.of(), false);
            }
        }
        return new Selection(-1, List.of(), true);
    }

    private static boolean write(
            List<String> lines,
            String key,
            int existingIndex,
            Set<String> values
    ) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        String next = key + ":" + array;
        if (existingIndex >= 0) {
            if (next.equals(lines.get(existingIndex))) return false;
            lines.set(existingIndex, next);
            return true;
        }
        lines.add(next);
        return true;
    }

    private static JsonObject parseObject(String raw) {
        JsonElement parsed = JsonParser.parseString(raw == null ? "{}" : raw);
        return parsed != null && parsed.isJsonObject()
                ? parsed.getAsJsonObject()
                : new JsonObject();
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String text(JsonObject root, String key, String fallback) {
        if (root == null || !root.has(key)) return fallback;
        try {
            return root.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeKey(String raw) {
        String value = raw == null ? "companion" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replace(':', '_').replaceAll("[^a-z0-9._/+\\-]", "_");
        return value.isBlank() ? "companion" : value;
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static Path gameDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path parent = config.getParent();
        return parent == null
                ? Path.of(".").toAbsolutePath().normalize()
                : parent;
    }

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("packs")
                .resolve("companion_resourcepacks.json");
    }

    private record Candidate(String key) {}

    private record Companion(
            String key,
            String packId,
            Path path,
            long modified
    ) {}

    private record Selection(int index, List<String> values, boolean valid) {}
}
