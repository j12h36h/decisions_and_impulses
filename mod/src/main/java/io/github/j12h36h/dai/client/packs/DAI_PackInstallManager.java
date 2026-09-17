package io.github.j12h36h.dai.client.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Transactional installer/uninstaller for DAI Worlds and managed packs. */
public final class DAI_PackInstallManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<String>> EXPERIENCE_SCAN_CACHE = new ConcurrentHashMap<>();
    private DAI_PackInstallManager() {}

    public static CompletableFuture<Result> installAsync(
            DAI_OfficialPackCatalog.PackEntry pack,
            String world
    ) {
        return CompletableFuture.supplyAsync(() -> install(pack, world));
    }

    public static CompletableFuture<Result> uninstallAsync(
            DAI_OfficialPackCatalog.PackEntry pack,
            String world
    ) {
        return CompletableFuture.supplyAsync(() -> uninstall(pack, world));
    }

    public static Optional<InstalledPack> installed(String packId, String world) {
        return readManifest().stream()
                .filter(pack -> pack.id().equals(packId))
                .filter(pack -> pack.world().isBlank()
                        || world == null
                        || world.isBlank()
                        || pack.world().equals(world))
                .findFirst();
    }

    public static List<InstalledPack> installedPacks() {
        return List.copyOf(readManifest());
    }

    public static List<String> worlds() {
        Path saves = gameDirectory().resolve("saves");
        if (!Files.isDirectory(saves)) return List.of();
        try (Stream<Path> paths = Files.list(saves)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to list saves for DAI Worlds.", exception);
            return List.of();
        }
    }

    public static Path resolveManagedResourceRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        Path root = gameDirectory().resolve(relativePath).normalize();
        return root.startsWith(gameDirectory()) ? root : null;
    }

    private static Result install(DAI_OfficialPackCatalog.PackEntry pack, String world) {
        if (pack == null || !pack.installable()) {
            return Result.fail("This DAI World has no installable components.");
        }
        if (!pack.compatible()) {
            return Result.fail(pack.compatibilityLabel());
        }

        List<InstalledPack> manifest = new ArrayList<>(readManifest());
        Optional<InstalledPack> previous = manifest.stream()
                .filter(entry -> entry.id().equals(pack.id()))
                .findFirst();

        if (previous.isPresent()
                && DAI_Versioning.compare(previous.get().version(), pack.version()) > 0) {
            return Result.fail("Installed version " + previous.get().version()
                    + " is newer than catalog version " + pack.version() + "; downgrade blocked.");
        }

        Path transaction = tempRoot().resolve(UUID.randomUUID().toString());
        List<PreparedComponent> prepared = new ArrayList<>();
        List<InstalledComponent> committed = new ArrayList<>();
        List<BackupComponent> backups = new ArrayList<>();

        try {
            Files.createDirectories(transaction);

            int index = 0;
            for (DAI_OfficialPackCatalog.ComponentEntry component : pack.components()) {
                URI uri = DAI_CurseForgeDownload.resolve(component);
                if (uri == null) {
                    return Result.fail("Invalid download source for " + component.id() + ".");
                }

                String safeName = DAI_PackFileOps.safeFileName(
                        component.fileName().isBlank() ? component.id() + ".zip" : component.fileName()
                );
                Path zip = transaction.resolve(String.format("%02d-%s", index++, safeName));
                DAI_PackFileOps.download(uri, zip);
                DAI_PackFileOps.validateZip(zip);
                DAI_PackFileOps.validateHash(zip, component.sha256());
                prepared.add(new PreparedComponent(component, zip));
            }

            if (previous.isPresent()) {
                backupInstalled(previous.get(), transaction.resolve("backup"), backups);
            }

            for (PreparedComponent component : prepared) {
                InstalledComponent installed = component.definition().type().equals("datapack")
                        ? commitDatapack(component)
                        : commitResourcePack(pack, component, transaction);
                committed.add(installed);
            }

            List<String> experienceIds = pack.isExperiencePack()
                    ? experienceIds(committed)
                    : List.of();

            previous.ifPresent(manifest::remove);
            manifest.add(new InstalledPack(
                    pack.id(),
                    pack.version(),
                    "",
                    pack.publicKind() == DAI_OfficialPackCatalog.PublicKind.ADDON ? "addon" : "experience_pack",
                    experienceIds,
                    committed
            ));
            writeManifest(manifest);

            refreshExperienceIndexes();
            safeReconcileResourcePacks();
            DAI_Core.LOGGER.info(
                    "<DAI>: Installed DAI World/pack '{}' version '{}' with {} component(s) and {} selectable experience definition(s).",
                    pack.id(), pack.version(), committed.size(), experienceIds.size()
            );
            String message = (previous.isPresent() ? "Updated " : "Installed ") + pack.name() + ".";
            if (pack.isExperiencePack() && !experienceIds.isEmpty()) {
                message += " " + experienceIds.size() + " experience"
                        + (experienceIds.size() == 1 ? " is" : "s are")
                        + " available under Play > New Singleplayer.";
            }
            return Result.ok(message);
        } catch (Exception exception) {
            rollbackCommitted(committed);
            restoreBackups(backups);
            DAI_Core.LOGGER.error("<DAI>: DAI World installation failed for '{}'; previous version restored.", pack.id(), exception);
            return Result.fail("Install failed: " + concise(exception));
        } finally {
            DAI_PackFileOps.deleteTreeQuietly(transaction);
        }
    }

    private static Result uninstall(DAI_OfficialPackCatalog.PackEntry pack, String world) {
        if (pack == null) return Result.fail("No DAI World selected.");

        List<InstalledPack> manifest = new ArrayList<>(readManifest());
        Optional<InstalledPack> installed = manifest.stream()
                .filter(entry -> entry.id().equals(pack.id()))
                .findFirst();

        if (installed.isEmpty()) return Result.fail("That DAI World is not installed.");

        Path transaction = tempRoot().resolve("uninstall-" + UUID.randomUUID());
        List<BackupComponent> backups = new ArrayList<>();
        try {
            Files.createDirectories(transaction);
            backupInstalled(installed.get(), transaction.resolve("backup"), backups);
            manifest.remove(installed.get());
            writeManifest(manifest);
            refreshExperienceIndexes();
            safeReconcileResourcePacks();
            DAI_Core.LOGGER.info("<DAI>: Uninstalled DAI World/pack '{}'.", pack.id());
            return Result.ok("Uninstalled " + pack.name() + ".");
        } catch (Exception exception) {
            restoreBackups(backups);
            DAI_Core.LOGGER.error("<DAI>: DAI World uninstall failed for '{}'.", pack.id(), exception);
            return Result.fail("Uninstall failed: " + concise(exception));
        } finally {
            DAI_PackFileOps.deleteTreeQuietly(transaction);
        }
    }

    private static void backupInstalled(
            InstalledPack pack,
            Path backupRoot,
            List<BackupComponent> backups
    ) throws IOException {
        Files.createDirectories(backupRoot);
        int index = 0;
        for (InstalledComponent component : pack.components()) {
            Path original = gameDirectory().resolve(component.path()).normalize();
            if (!original.startsWith(gameDirectory()) || !Files.exists(original)) continue;
            Path backup = backupRoot.resolve(String.format("%02d-%s", index++, DAI_PackFileOps.safePathName(component.id())));
            Files.createDirectories(backup.getParent());
            Files.move(original, backup, StandardCopyOption.REPLACE_EXISTING);
            backups.add(new BackupComponent(original, backup, component.directory()));
        }
    }

    private static void restoreBackups(List<BackupComponent> backups) {
        for (BackupComponent backup : backups) {
            try {
                if (!Files.exists(backup.backup())) continue;
                if (backup.directory()) DAI_PackFileOps.deleteTreeQuietly(backup.original());
                else Files.deleteIfExists(backup.original());
                Files.createDirectories(backup.original().getParent());
                Files.move(backup.backup(), backup.original(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception restoreException) {
                DAI_Core.LOGGER.error("<DAI>: Failed to restore '{}' during rollback.", backup.original(), restoreException);
            }
        }
    }

    private static void rollbackCommitted(List<InstalledComponent> components) {
        for (InstalledComponent component : components) {
            Path path = gameDirectory().resolve(component.path()).normalize();
            if (!path.startsWith(gameDirectory())) continue;
            try {
                if (component.directory()) DAI_PackFileOps.deleteTreeQuietly(path);
                else Files.deleteIfExists(path);
            } catch (Exception ignored) { }
        }
    }

    private static InstalledComponent commitDatapack(PreparedComponent component) throws IOException {
        Path datapacks = DAI_GlobalDatapackLibrary.initialize();
        Files.createDirectories(datapacks);
        String fileName = DAI_PackFileOps.safeFileName(component.definition().fileName());
        if (fileName.isBlank()) fileName = DAI_PackFileOps.safeFileName(component.definition().id() + ".zip");
        Path target = datapacks.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(datapacks)) throw new IOException("Invalid datapack filename.");
        DAI_PackFileOps.moveReplacing(component.zip(), target);
        return new InstalledComponent(component.definition().id(), "datapack", relative(target), false);
    }

    private static InstalledComponent commitResourcePack(
            DAI_OfficialPackCatalog.PackEntry pack,
            PreparedComponent component,
            Path transaction
    ) throws IOException {
        String packKey = DAI_PackFileOps.safePathName(pack.id());
        String componentKey = DAI_PackFileOps.safePathName(component.definition().id());
        Path staged = transaction.resolve("extract-" + componentKey);
        DAI_PackFileOps.extractZip(component.zip(), staged);
        Path actualRoot = DAI_PackFileOps.locatePackRoot(staged);
        if (actualRoot == null) throw new IOException("Resource pack does not contain pack.mcmeta.");

        Path targetRoot = managedResourceRoot().resolve(packKey).resolve(componentKey);
        DAI_PackFileOps.deleteTreeQuietly(targetRoot);
        Files.createDirectories(targetRoot.getParent());
        DAI_PackFileOps.moveDirectory(actualRoot, targetRoot);
        return new InstalledComponent(component.definition().id(), "resource_pack", relative(targetRoot), true);
    }

    private static void safeReconcileResourcePacks() {
        try {
            DAI_ManagedResourcePackPreferences.reconcileSavedSelection();
            DAI_ManagedResourcePackPreferences.reconcileLiveSelection();
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn("<DAI>: Installed files successfully but could not immediately reconcile resource-pack selection.", exception);
        }
    }

    private static List<InstalledPack> readManifest() {
        Path path = manifestPath();
        if (!Files.isRegularFile(path)) return new ArrayList<>();
        List<InstalledPack> result = new ArrayList<>();
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) return result;
            JsonArray packs = parsed.getAsJsonObject().getAsJsonArray("packs");
            if (packs == null) return result;
            for (JsonElement element : packs) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                JsonArray components = object.getAsJsonArray("components");
                List<InstalledComponent> installedComponents = new ArrayList<>();
                if (components != null) {
                    for (JsonElement componentElement : components) {
                        if (!componentElement.isJsonObject()) continue;
                        JsonObject component = componentElement.getAsJsonObject();
                        installedComponents.add(new InstalledComponent(
                                text(component, "id", "component"),
                                text(component, "type", "datapack"),
                                text(component, "path", ""),
                                bool(component, "directory", false)
                        ));
                    }
                }
                String id = text(object, "id", "");
                if (id.isBlank()) continue;
                String publicType = installedPublicType(id, text(object, "public_type", ""));
                List<String> experienceIds = strings(object, "experience_ids");
                if (experienceIds.isEmpty() && "experience_pack".equals(publicType)) {
                    experienceIds = experienceIds(installedComponents);
                }
                result.add(new InstalledPack(
                        id,
                        text(object, "version", ""),
                        text(object, "world", ""),
                        publicType,
                        experienceIds,
                        installedComponents
                ));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Failed to read installed-pack manifest '{}'.", path, exception);
        }
        return result;
    }

    private static void writeManifest(List<InstalledPack> packs) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", 4);
        root.addProperty("engine", DAI_Core.FEATURE_LEVEL);
        JsonArray array = new JsonArray();
        for (InstalledPack pack : packs) {
            JsonObject object = new JsonObject();
            object.addProperty("id", pack.id());
            object.addProperty("version", pack.version());
            object.addProperty("world", pack.world());
            object.addProperty("public_type", pack.publicType());
            JsonArray experienceIds = new JsonArray();
            for (String experienceId : pack.experienceIds()) experienceIds.add(experienceId);
            object.add("experience_ids", experienceIds);
            JsonArray components = new JsonArray();
            for (InstalledComponent component : pack.components()) {
                JsonObject value = new JsonObject();
                value.addProperty("id", component.id());
                value.addProperty("type", component.type());
                value.addProperty("path", component.path());
                value.addProperty("directory", component.directory());
                components.add(value);
            }
            object.add("components", components);
            array.add(object);
        }
        root.add("packs", array);

        Path target = manifestPath();
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        DAI_PackFileOps.moveReplacing(temp, target);
    }

    private static List<String> experienceIds(List<InstalledComponent> components) {
        if (components == null || components.isEmpty()) return List.of();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (InstalledComponent component : components) {
            if (component == null || !"datapack".equals(component.type())) continue;
            Path pack = gameDirectory().resolve(component.path()).normalize();
            if (!pack.startsWith(gameDirectory()) || !Files.exists(pack)) continue;
            ids.addAll(cachedExperienceIds(pack));
        }
        return normalizeExperienceIds(ids);
    }

    private static List<String> cachedExperienceIds(Path pack) {
        try {
            long modified = Files.getLastModifiedTime(pack).toMillis();
            long size = Files.isRegularFile(pack) ? Files.size(pack) : 0L;
            String key = pack.toAbsolutePath().normalize() + "|" + modified + "|" + size;
            return EXPERIENCE_SCAN_CACHE.computeIfAbsent(
                    key,
                    ignored -> normalizeExperienceIds(DAI_ExperienceRepository.definitionsInPack(pack).keySet())
            );
        } catch (Exception exception) {
            return normalizeExperienceIds(DAI_ExperienceRepository.definitionsInPack(pack).keySet());
        }
    }

    private static List<String> normalizeExperienceIds(Iterable<String> values) {
        if (values == null) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private static List<String> strings(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) return List.of();
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray(key)) {
            try {
                String value = element.getAsString();
                if (!value.isBlank()) values.add(value);
            } catch (Exception ignored) { }
        }
        return normalizeExperienceIds(values);
    }

    private static void refreshExperienceIndexes() {
        try {
            DAI_ExperienceRepository.reloadSelectable();
            DAI_ExperienceRepository.reload();
            DAI_WorldgenRepository.reload();
        } catch (RuntimeException exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Pack files changed successfully, but the installed experience index could not refresh immediately.",
                    exception
            );
        }
    }

    private static String installedPublicType(String id, String stored) {
        String value = stored == null ? "" : stored.trim().toLowerCase();
        if (value.equals("addon") || value.equals("experience_pack")) return value;

        try {
            for (DAI_OfficialPackCatalog.PackEntry pack : DAI_OfficialPackService.cachedOrFallback().packs()) {
                if (!pack.id().equals(id)) continue;
                return pack.isAddon() ? "addon" : "experience_pack";
            }
        } catch (Exception ignored) { }
        return "legacy";
    }

    private static Path manifestPath() {
        return FMLPaths.CONFIGDIR.get().resolve(DAI_Core.MODID).resolve("packs").resolve("installed.json");
    }

    public static Path managedResourceRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(DAI_Core.MODID).resolve("managed_resourcepacks");
    }

    private static Path tempRoot() {
        return FMLPaths.CONFIGDIR.get().resolve(DAI_Core.MODID).resolve("packs").resolve("tmp");
    }

    public static Path gameDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path parent = config.getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    private static String relative(Path path) {
        return gameDirectory().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String text(JsonObject root, String key, String fallback) {
        if (!root.has(key)) return fallback;
        try { return root.get(key).getAsString(); } catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (!root.has(key)) return fallback;
        try { return root.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    public record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }

    public record InstalledPack(
            String id,
            String version,
            String world,
            String publicType,
            List<String> experienceIds,
            List<InstalledComponent> components
    ) {
        public InstalledPack {
            id = id == null ? "" : id;
            version = version == null ? "" : version;
            world = world == null ? "" : world;
            publicType = publicType == null ? "legacy" : publicType.trim().toLowerCase(Locale.ROOT);
            experienceIds = normalizeExperienceIds(experienceIds);
            components = components == null ? List.of() : List.copyOf(components);
        }

        public boolean isAddon() {
            return "addon".equals(publicType);
        }

        public boolean isExperiencePack() {
            return "experience_pack".equals(publicType);
        }

        public boolean ownsExperience(String experienceId) {
            if (!isExperiencePack() || experienceId == null || experienceId.isBlank()) return false;
            String normalized = experienceId.trim().toLowerCase(Locale.ROOT);
            return experienceIds.contains(normalized);
        }
    }

    public record InstalledComponent(String id, String type, String path, boolean directory) {
        public InstalledComponent {
            id = id == null ? "" : id;
            type = type == null ? "" : type;
            path = path == null ? "" : path;
        }
    }

    private record PreparedComponent(DAI_OfficialPackCatalog.ComponentEntry definition, Path zip) {}
    private record BackupComponent(Path original, Path backup, boolean directory) {}
}
