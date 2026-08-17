package io.github.j12h36h.dai.client.packs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Transactional installer/uninstaller for packs owned by D.A.I. */
public final class DAI_PackInstallManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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

    public static Optional<InstalledPack> installed(
            String packId,
            String world
    ) {
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
            DAI_Core.LOGGER.warn("<DAI>: Failed to list saves for pack browser.", exception);
            return List.of();
        }
    }

    public static Path resolveManagedResourceRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        Path root = gameDirectory().resolve(relativePath).normalize();
        return root.startsWith(gameDirectory()) ? root : null;
    }

    private static Result install(
            DAI_OfficialPackCatalog.PackEntry pack,
            String world
    ) {
        if (pack == null || !pack.installable()) {
            return Result.fail("This catalog entry has no installable components.");
        }

        Path transaction = tempRoot().resolve(UUID.randomUUID().toString());
        List<PreparedComponent> prepared = new ArrayList<>();

        try {
            Files.createDirectories(transaction);

            int index = 0;
            for (DAI_OfficialPackCatalog.ComponentEntry component : pack.components()) {
                URI uri = DAI_CurseForgeDownload.resolve(component);
                if (uri == null) {
                    return Result.fail("Invalid CurseForge download for " + component.id() + ".");
                }

                String safeName = DAI_PackFileOps.safeFileName(
                        component.fileName().isBlank()
                                ? component.id() + ".zip"
                                : component.fileName()
                );
                Path zip = transaction.resolve(String.format("%02d-%s", index++, safeName));
                DAI_PackFileOps.download(uri, zip);
                DAI_PackFileOps.validateZip(zip);
                DAI_PackFileOps.validateHash(zip, component.sha256());
                prepared.add(new PreparedComponent(component, zip));
            }

            List<InstalledPack> manifest = new ArrayList<>(readManifest());
            // One managed installation per catalog pack id. Datapacks now
            // live in DAI's global <game>/datapacks library, so reinstalling
            // updates that single managed source copy.
            removeMatching(manifest, pack.id(), "");

            List<InstalledComponent> installedComponents = new ArrayList<>();
            for (PreparedComponent component : prepared) {
                if (component.definition().type().equals("datapack")) {
                    installedComponents.add(
                            commitDatapack(component)
                    );
                } else {
                    installedComponents.add(
                            commitResourcePack(pack, component, transaction)
                    );
                }
            }

            manifest.add(new InstalledPack(
                    pack.id(),
                    pack.version(),
                    "",
                    installedComponents
            ));
            writeManifest(manifest);
            DAI_ManagedResourcePackPreferences.reconcileSavedSelection();
            DAI_ManagedResourcePackPreferences.reconcileLiveSelection();

            DAI_Core.LOGGER.info(
                    "<DAI>: Installed official pack '{}' version '{}' with {} component(s).",
                    pack.id(),
                    pack.version(),
                    installedComponents.size()
            );

            return Result.ok("Installed " + pack.name() + ".");
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Pack installation failed for '{}'.", pack.id(), exception);
            return Result.fail("Install failed: " + concise(exception));
        } finally {
            DAI_PackFileOps.deleteTreeQuietly(transaction);
        }
    }

    private static Result uninstall(
            DAI_OfficialPackCatalog.PackEntry pack,
            String world
    ) {
        if (pack == null) return Result.fail("No pack selected.");

        List<InstalledPack> manifest = new ArrayList<>(readManifest());
        Optional<InstalledPack> installed = manifest.stream()
                .filter(entry -> entry.id().equals(pack.id()))
                .filter(entry -> !pack.needsWorld() || entry.world().equals(world))
                .findFirst();

        if (installed.isEmpty()) {
            return Result.fail("That pack is not installed.");
        }

        try {
            for (InstalledComponent component : installed.get().components()) {
                Path path = gameDirectory().resolve(component.path()).normalize();
                if (!path.startsWith(gameDirectory())) continue;
                if (component.directory()) {
                    DAI_PackFileOps.deleteTree(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }

            manifest.remove(installed.get());
            writeManifest(manifest);
            DAI_ManagedResourcePackPreferences.reconcileSavedSelection();
            DAI_ManagedResourcePackPreferences.reconcileLiveSelection();

            DAI_Core.LOGGER.info("<DAI>: Uninstalled official pack '{}'.", pack.id());
            return Result.ok("Uninstalled " + pack.name() + ".");
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Pack uninstall failed for '{}'.", pack.id(), exception);
            return Result.fail("Uninstall failed: " + concise(exception));
        }
    }

    private static InstalledComponent commitDatapack(
            PreparedComponent component
    ) throws IOException {
        Path datapacks = DAI_GlobalDatapackLibrary.initialize();
        Files.createDirectories(datapacks);

        String fileName = DAI_PackFileOps.safeFileName(component.definition().fileName());
        Path target = datapacks.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(datapacks)) throw new IOException("Invalid datapack filename.");

        DAI_PackFileOps.moveReplacing(component.zip(), target);
        DAI_Core.LOGGER.info(
                "<DAI>: Installed datapack component '{}' into global library '{}'.",
                component.definition().id(),
                target
        );

        return new InstalledComponent(
                component.definition().id(),
                "datapack",
                relative(target),
                false
        );
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
        if (actualRoot == null) {
            throw new IOException("Resource pack does not contain pack.mcmeta.");
        }

        Path managedBase = managedResourceRoot().resolve(packKey).resolve(componentKey);
        DAI_PackFileOps.deleteTreeQuietly(managedBase);
        Files.createDirectories(managedBase.getParent());

        Path targetRoot = managedBase;
        DAI_PackFileOps.moveDirectory(actualRoot, targetRoot);

        return new InstalledComponent(
                component.definition().id(),
                "resource_pack",
                relative(targetRoot),
                true
        );
    }

    private static boolean validWorld(String world) {
        return world != null && !world.isBlank() && worlds().contains(world);
    }

    private static void removeMatching(List<InstalledPack> manifest, String id, String world) {
        List<InstalledPack> replaced = manifest.stream()
                .filter(entry -> entry.id().equals(id))
                .filter(entry -> world.isBlank() || entry.world().equals(world))
                .toList();
        for (InstalledPack old : replaced) {
            for (InstalledComponent component : old.components()) {
                Path path = gameDirectory().resolve(component.path()).normalize();
                if (!path.startsWith(gameDirectory())) continue;
                if (component.directory()) DAI_PackFileOps.deleteTreeQuietly(path);
                else {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                }
            }
            manifest.remove(old);
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
                result.add(new InstalledPack(
                        text(object, "id", ""),
                        text(object, "version", ""),
                        text(object, "world", ""),
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
        root.addProperty("format", 1);
        JsonArray array = new JsonArray();

        for (InstalledPack pack : packs) {
            JsonObject object = new JsonObject();
            object.addProperty("id", pack.id());
            object.addProperty("version", pack.version());
            object.addProperty("world", pack.world());
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

    private static Path manifestPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("packs")
                .resolve("installed.json");
    }

    private static Path managedResourceRoot() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("managed_resourcepacks");
    }

    private static Path tempRoot() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("packs")
                .resolve("tmp");
    }

    private static Path gameDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path parent = config.getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    private static String relative(Path path) {
        return gameDirectory().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String concise(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
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
            List<InstalledComponent> components
    ) {
        public InstalledPack {
            id = id == null ? "" : id;
            version = version == null ? "" : version;
            world = world == null ? "" : world;
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    public record InstalledComponent(
            String id,
            String type,
            String path,
            boolean directory
    ) {
        public InstalledComponent {
            id = id == null ? "" : id;
            type = type == null ? "" : type;
            path = path == null ? "" : path;
        }
    }

    private record PreparedComponent(
            DAI_OfficialPackCatalog.ComponentEntry definition,
            Path zip
    ) {}
}
