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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stable-identity synchronization for DAI-managed datapacks.
 *
 * The global <game>/datapacks directory is DAI's update/source library. When a
 * world already owns a DAI pack with the same stable identity, the newest
 * global copy replaces the save-local mirror even if the versioned filename
 * changed. Unrelated world datapacks are never touched.
 */
public final class DAI_DatapackSync {

    private static final Pattern VERSIONED_FILENAME = Pattern.compile(
            "(?i)(?:^|[_\\-.])v(\\d+(?:\\.\\d+){1,4})(?=$|[_\\-.])"
    );

    private DAI_DatapackSync() {}

    public static SyncResult reconcileExistingWorldPacks(Path worldDatapacks) {
        if (worldDatapacks == null) return SyncResult.EMPTY;

        Path targetRoot = worldDatapacks.toAbsolutePath().normalize();
        if (!Files.isDirectory(targetRoot)) return SyncResult.EMPTY;

        Map<String, Path> global = globalWinners();
        if (global.isEmpty()) return SyncResult.EMPTY;

        List<Replacement> replacements = new ArrayList<>();
        LinkedHashSet<String> changedNames = new LinkedHashSet<>();

        try (Stream<Path> entries = Files.list(targetRoot)) {
            List<Path> worldPacks = entries.sorted().toList();
            Map<String, List<Path>> byIdentity = new LinkedHashMap<>();

            for (Path pack : worldPacks) {
                if (!isWorldDatapackCandidate(pack)) continue;
                if (DAI_DatapackMetadata.role(pack) == DAI_DatapackRole.UNMANAGED) continue;

                String identity = identity(pack);
                if (identity.isBlank()) continue;
                byIdentity.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(pack);
            }

            for (Map.Entry<String, Path> entry : global.entrySet()) {
                try {
                    List<Path> installed = byIdentity.get(entry.getKey());
                    if (installed == null || installed.isEmpty()) continue;

                    Path source = entry.getValue().toAbsolutePath().normalize();
                    Path target = targetRoot.resolve(source.getFileName().toString()).normalize();
                    if (!target.startsWith(targetRoot)) continue;

                    boolean targetWasInstalled = installed.stream()
                            .map(path -> path.toAbsolutePath().normalize())
                            .anyMatch(target::equals);
                    boolean targetNeedsUpdate = !targetWasInstalled || !samePackContent(source, target);

                    // Install the current source first. This means a Windows file
                    // lock on the stale filename can never prevent the replacement
                    // pack itself from becoming available for the reload.
                    if (targetNeedsUpdate) {
                        copyPack(source, target);
                        changedNames.add(target.getFileName().toString());

                        // Same-filename content replacement still matters for a
                        // selected pack because the server must reload its data.
                        if (targetWasInstalled) {
                            replacements.add(new Replacement(
                                    entry.getKey(),
                                    target.getFileName().toString(),
                                    target,
                                    DAI_DatapackMetadata.role(source)
                            ));
                        }

                        DAI_Core.LOGGER.info(
                                "<DAI>: Synchronized world datapack identity '{}' to current global pack '{}'.",
                                entry.getKey(),
                                source.getFileName()
                        );
                    }

                    int staleCount = 0;
                    for (Path old : installed) {
                        Path normalizedOld = old.toAbsolutePath().normalize();
                        if (normalizedOld.equals(target)) continue;

                        String oldName = old.getFileName().toString();
                        boolean deleted = tryDeleteRecursively(old);
                        changedNames.add(oldName);
                        replacements.add(new Replacement(
                                entry.getKey(),
                                oldName,
                                target,
                                DAI_DatapackMetadata.role(source)
                        ));
                        if (deleted) {
                            staleCount++;
                        } else {
                            DAI_Core.LOGGER.warn(
                                    "<DAI>: Could not delete stale world datapack '{}' (likely temporarily locked); it will be deselected in favor of '{}'.",
                                    old.getFileName(),
                                    target.getFileName()
                            );
                        }
                    }

                    if (!targetNeedsUpdate && staleCount > 0) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Removed {} stale world datapack copy/copies for identity '{}'; keeping '{}'.",
                                staleCount,
                                entry.getKey(),
                                target.getFileName()
                        );
                    }
                } catch (Exception identityFailure) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Could not synchronize world datapack identity '{}' from global source '{}'; other managed packs will continue processing.",
                            entry.getKey(),
                            entry.getValue().getFileName(),
                            identityFailure
                    );
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not synchronize DAI-managed world datapacks from the global library '{}'.",
                    DAI_GlobalDatapackLibrary.initialize(),
                    exception
            );
        }

        if (replacements.isEmpty() && changedNames.isEmpty()) return SyncResult.EMPTY;
        return new SyncResult(List.copyOf(replacements), Set.copyOf(changedNames));
    }

    /** Stable key: explicit metadata first, then authored non-vanilla namespaces. */
    public static String identity(Path pack) {
        if (pack == null || !Files.exists(pack)) return "";

        try {
            JsonObject metadata = readPackMeta(pack);
            JsonObject dai = metadata != null
                    && metadata.has("dai")
                    && metadata.get("dai").isJsonObject()
                    ? metadata.getAsJsonObject("dai")
                    : null;

            String explicit = firstText(
                    dai,
                    "pack_id", "packId", "identity", "id",
                    "companion_id", "companionId", "project_id", "projectId"
            );
            if (!explicit.isBlank()) return "explicit:" + normalizeKey(explicit);

            Set<String> namespaces = dataNamespaces(pack);
            namespaces.remove("minecraft");
            namespaces.remove("forge");
            namespaces.remove("neoforge");
            namespaces.remove("c");
            if (namespaces.isEmpty()) return "";

            return "namespace:" + String.join("+", namespaces.stream().sorted().toList());
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not resolve stable datapack identity for '{}'.",
                    pack,
                    exception
            );
            return "";
        }
    }

    private static Map<String, Path> globalWinners() {
        LinkedHashMap<String, Path> result = new LinkedHashMap<>();
        Path root = DAI_GlobalDatapackLibrary.initialize();
        if (!Files.isDirectory(root)) return result;

        try (Stream<Path> entries = Files.list(root)) {
            for (Path pack : entries.sorted().toList()) {
                if (!isWorldDatapackCandidate(pack)) continue;
                if (DAI_DatapackMetadata.role(pack) == DAI_DatapackRole.UNMANAGED) continue;

                String identity = identity(pack);
                if (identity.isBlank()) continue;

                Path existing = result.get(identity);
                if (existing == null || prefer(pack, existing) > 0) {
                    result.put(identity, pack.toAbsolutePath().normalize());
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not enumerate global DAI datapacks for version synchronization.",
                    exception
            );
        }
        return result;
    }

    private static int prefer(Path left, Path right) {
        int version = compareVersion(version(left), version(right));
        if (version != 0) return version;

        try {
            int modified = Long.compare(
                    Files.getLastModifiedTime(left).toMillis(),
                    Files.getLastModifiedTime(right).toMillis()
            );
            if (modified != 0) return modified;
        } catch (Exception ignored) { }

        return left.getFileName().toString().compareToIgnoreCase(right.getFileName().toString());
    }

    private static String version(Path pack) {
        try {
            JsonObject root = readPackMeta(pack);
            JsonObject dai = root != null && root.has("dai") && root.get("dai").isJsonObject()
                    ? root.getAsJsonObject("dai")
                    : null;
            String explicit = firstText(dai, "version", "pack_version", "packVersion");
            if (!explicit.isBlank()) return explicit;
        } catch (Exception ignored) { }

        String name = pack == null ? "" : pack.getFileName().toString();
        Matcher matcher = VERSIONED_FILENAME.matcher(name);
        if (matcher.find()) return matcher.group(1);
        return "0";
    }

    private static int compareVersion(String left, String right) {
        int[] a = numericVersion(left);
        int[] b = numericVersion(right);
        int size = Math.max(a.length, b.length);
        for (int i = 0; i < size; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            int compare = Integer.compare(av, bv);
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static int[] numericVersion(String value) {
        String raw = value == null ? "" : value.trim();
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)*)").matcher(raw);
        if (!matcher.find()) return new int[]{0};
        String[] parts = matcher.group(1).split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (Exception ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static JsonObject readPackMeta(Path pack) throws Exception {
        if (Files.isDirectory(pack)) {
            Path meta = pack.resolve("pack.mcmeta");
            if (!Files.isRegularFile(meta)) return null;
            JsonElement parsed = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry entry = zip.getEntry("pack.mcmeta");
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream input = zip.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        }
    }

    private static Set<String> dataNamespaces(Path pack) throws Exception {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (Files.isDirectory(pack)) {
            Path data = pack.resolve("data");
            if (!Files.isDirectory(data)) return result;
            try (Stream<Path> namespaces = Files.list(data)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    String value = namespace.getFileName().toString().trim().toLowerCase(Locale.ROOT);
                    if (!value.isBlank()) result.add(value);
                }
            }
            return result;
        }

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("data/") || name.length() <= 5) continue;
                String remaining = name.substring(5);
                int slash = remaining.indexOf('/');
                if (slash <= 0) continue;
                String namespace = remaining.substring(0, slash).trim().toLowerCase(Locale.ROOT);
                if (!namespace.isBlank()) result.add(namespace);
            }
        }
        return result;
    }

    private static String firstText(JsonObject root, String... keys) {
        if (root == null || keys == null) return "";
        for (String key : keys) {
            if (key == null || !root.has(key)) continue;
            try {
                String value = root.get(key).getAsString().trim();
                if (!value.isBlank()) return value;
            } catch (Exception ignored) { }
        }
        return "";
    }

    private static String normalizeKey(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static boolean samePackContent(Path source, Path target) {
        if (source == null || target == null) return false;
        try {
            if (!Files.exists(source) || !Files.exists(target)) return false;

            boolean sourceDirectory = Files.isDirectory(source);
            boolean targetDirectory = Files.isDirectory(target);
            if (sourceDirectory != targetDirectory) return false;

            if (!sourceDirectory) {
                if (Files.size(source) != Files.size(target)) return false;
                return Files.mismatch(source, target) == -1L;
            }

            try (Stream<Path> sourceFiles = Files.walk(source);
                 Stream<Path> targetFiles = Files.walk(target)) {
                List<Path> left = sourceFiles.filter(Files::isRegularFile)
                        .map(source::relativize)
                        .sorted()
                        .toList();
                List<Path> right = targetFiles.filter(Files::isRegularFile)
                        .map(target::relativize)
                        .sorted()
                        .toList();
                if (!left.equals(right)) return false;

                for (Path relative : left) {
                    Path a = source.resolve(relative);
                    Path b = target.resolve(relative);
                    if (Files.size(a) != Files.size(b) || Files.mismatch(a, b) != -1L) {
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void copyPack(Path source, Path target) throws Exception {
        if (Files.exists(target) && Files.isDirectory(source) != Files.isDirectory(target)) {
            deleteRecursively(target);
        }

        if (Files.isDirectory(source)) {
            deleteRecursively(target);
            Files.createDirectories(target);
            try (Stream<Path> files = Files.walk(source)) {
                for (Path path : files.toList()) {
                    Path destination = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            return;
        }

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean tryDeleteRecursively(Path path) {
        try {
            deleteRecursively(path);
            return !Files.exists(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) return;
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
            return;
        }

        try (Stream<Path> files = Files.walk(path)) {
            for (Path entry : files.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static boolean isWorldDatapackCandidate(Path path) {
        if (path == null) return false;
        if (Files.isDirectory(path)) return Files.isRegularFile(path.resolve("pack.mcmeta"));
        return Files.isRegularFile(path)
                && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public record Replacement(
            String identity,
            String oldFileName,
            Path newPath,
            DAI_DatapackRole role
    ) { }

    public record SyncResult(
            List<Replacement> replacements,
            Set<String> changedFileNames
    ) {
        private static final SyncResult EMPTY = new SyncResult(List.of(), Set.of());

        public boolean changed() {
            return !replacements.isEmpty() || !changedFileNames.isEmpty();
        }
    }
}
