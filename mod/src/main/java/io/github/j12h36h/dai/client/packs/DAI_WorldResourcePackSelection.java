package io.github.j12h36h.dai.client.packs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

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

/**
 * Per-world client resource-pack activation.
 *
 * The world stores only the pack ids it wants DAI to temporarily add on join.
 * The player's pre-world resource-pack selection is snapshotted before launch
 * and restored after unload, so world-specific visuals never leak into the DAI
 * shell or the next save.
 */
public final class DAI_WorldResourcePackSelection {

    private static final String METADATA = "dai/resourcepacks.json";

    private static String armedSaveId = "";
    private static String activeSaveId = "";
    private static List<String> baselineEnabled;
    private static List<String> baselineIncompatible;
    private static boolean restoring;
    private static int generation;

    private DAI_WorldResourcePackSelection() {}

    /** Packs visible in Modify World. Normal /resourcepacks and DAI-managed packs are included. */
    public static List<PackEntry> available(String saveId) {
        LinkedHashMap<String, String> available = new LinkedHashMap<>();

        Path root = gameDirectory().resolve("resourcepacks");
        if (Files.isDirectory(root)) {
            try (var stream = Files.list(root)) {
                for (Path path : stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).toList()) {
                    if (!isResourcePackCandidate(path)) continue;
                    String fileName = path.getFileName().toString();
                    available.put("file/" + fileName, label(fileName));
                }
            } catch (Exception exception) {
                DAI_Core.LOGGER.warn("<DAI>: Could not enumerate normal resource packs for Modify World.", exception);
            }
        }

        // Managed public-pack resources do not live in /resourcepacks, so add
        // them directly from the install manifest.
        try {
            for (DAI_PackInstallManager.InstalledPack installed : DAI_PackInstallManager.installedPacks()) {
                for (DAI_PackInstallManager.InstalledComponent component : installed.components()) {
                    if (!"resource_pack".equals(component.type())) continue;
                    Path managedRoot = DAI_PackInstallManager.resolveManagedResourceRoot(component.path());
                    if (managedRoot == null || !Files.isRegularFile(managedRoot.resolve("pack.mcmeta"))) continue;
                    String id = DAI_ManagedResourcePackPreferences.packId(installed, component);
                    String packLabel = installed.world() == null || installed.world().isBlank()
                            ? label(installed.id())
                            : installed.world().trim();
                    available.put(id, packLabel);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not enumerate managed resource packs for Modify World: {}", exception.toString());
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) {
            for (String id : minecraft.options.resourcePacks) {
                if (id == null || id.isBlank()) continue;
                available.putIfAbsent(id, label(id));
            }
        }

        Set<String> selected = read(savePath(saveId));
        ArrayList<PackEntry> result = new ArrayList<>(available.size());
        for (Map.Entry<String, String> entry : available.entrySet()) {
            result.add(new PackEntry(entry.getKey(), entry.getValue(), selected.contains(entry.getKey())));
        }
        result.sort(Comparator.comparing(PackEntry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static boolean setSelected(String saveId, String packId, boolean selected) {
        if (saveId == null || saveId.isBlank() || packId == null || packId.isBlank()) return false;
        Path save = savePath(saveId);
        if (save == null || !Files.isDirectory(save)) return false;

        LinkedHashSet<String> values = new LinkedHashSet<>(read(save));
        if (selected) values.add(packId.trim());
        else values.remove(packId.trim());
        return write(save, values);
    }

    public static Set<String> readForSave(String saveId) {
        return read(savePath(saveId));
    }

    /** Arms a save so the experience launch barrier can include its world-specific packs. */
    public static synchronized void arm(String saveId) {
        armedSaveId = saveId == null ? "" : saveId.trim();
    }

    /** Snapshot of the packs requested by the currently armed save. */
    static synchronized Set<String> requestedForArmed() {
        if (armedSaveId.isBlank()) return Set.of();
        return read(savePath(armedSaveId));
    }

    /** Captures the pre-world user selection before an experience mutates it. */
    public static synchronized void captureBaselineForArmed(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || armedSaveId.isBlank()) return;
        if (baselineEnabled == null) {
            baselineEnabled = List.copyOf(minecraft.options.resourcePacks);
            baselineIncompatible = List.copyOf(minecraft.options.incompatibleResourcePacks);
        }
        activeSaveId = armedSaveId;
    }

    /** Adds the armed world's requested packs to the current live stack. */
    public static synchronized boolean applyArmedSelectionNow(Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null || armedSaveId.isBlank()) return false;
        captureBaselineForArmed(minecraft);

        Set<String> selected = read(savePath(armedSaveId));
        armedSaveId = "";
        if (selected.isEmpty()) return false;

        LinkedHashSet<String> enabled = new LinkedHashSet<>(minecraft.options.resourcePacks);
        enabled.addAll(selected);
        LinkedHashSet<String> incompatible = new LinkedHashSet<>(minecraft.options.incompatibleResourcePacks);
        incompatible.removeAll(selected);

        List<String> nextEnabled = new ArrayList<>(enabled);
        List<String> nextIncompatible = new ArrayList<>(incompatible);
        boolean changed = !nextEnabled.equals(minecraft.options.resourcePacks)
                || !nextIncompatible.equals(minecraft.options.incompatibleResourcePacks);
        if (!changed) return false;

        minecraft.options.resourcePacks = nextEnabled;
        minecraft.options.incompatibleResourcePacks = nextIncompatible;
        minecraft.options.save();
        DAI_Core.LOGGER.info(
                "<DAI>: Armed {} world-specific resource pack(s) for save '{}'.",
                selected.size(), activeSaveId
        );
        return true;
    }

    /**
     * Generic-world equivalent of the experience resource-pack barrier. The
     * target open callback is invoked only after any requested pack reload has
     * completed.
     */
    public static void applyBeforeWorldLaunch(String saveId, Runnable afterReady) {
        if (afterReady == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            afterReady.run();
            return;
        }

        arm(saveId);
        final int token = ++generation;
        minecraft.execute(() -> {
            captureBaselineForArmed(minecraft);
            Set<String> requested = requestedForArmed();
            boolean changed = applyArmedSelectionNow(minecraft);
            boolean repositoryChanged = DAI_ResourcePackRepositorySync.syncFromOptions(
                    minecraft,
                    requested
            );
            if (!changed && !repositoryChanged) {
                afterReady.run();
                return;
            }
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                if (token != generation) return;
                if (error != null) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: World-specific resource-pack reload failed before opening '{}'; continuing with selected options.",
                            saveId, error
                    );
                } else {
                    DAI_Core.LOGGER.info(
                            "<DAI>: World-specific resource packs are live for '{}'.", saveId
                    );
                }
                afterReady.run();
            }));
        });
    }

    /** Restores the pre-world selection into the live options object. Caller decides whether to reload. */
    public static synchronized boolean restoreLiveSelectionNow(Minecraft minecraft) {
        armedSaveId = "";
        if (minecraft == null || minecraft.options == null || baselineEnabled == null) {
            activeSaveId = "";
            return false;
        }

        List<String> nextEnabled = new ArrayList<>(baselineEnabled);
        List<String> nextIncompatible = baselineIncompatible == null
                ? new ArrayList<>()
                : new ArrayList<>(baselineIncompatible);
        boolean changed = !nextEnabled.equals(minecraft.options.resourcePacks)
                || !nextIncompatible.equals(minecraft.options.incompatibleResourcePacks);

        minecraft.options.resourcePacks = nextEnabled;
        minecraft.options.incompatibleResourcePacks = nextIncompatible;
        minecraft.options.save();

        String unloaded = activeSaveId;
        activeSaveId = "";
        baselineEnabled = null;
        baselineIncompatible = null;

        if (changed) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Restored the pre-world resource-pack selection after unloading '{}'.",
                    unloaded
            );
        }
        return changed;
    }

    /** Clears a failed launch and restores the player's pack stack asynchronously. */
    public static void cancelLaunchAndRestore() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        beginRestoreBarrier();
        minecraft.execute(() -> {
            boolean changed = restoreLiveSelectionNow(minecraft);
            boolean repositoryChanged = DAI_ResourcePackRepositorySync.syncFromOptions(
                    minecraft,
                    Set.of()
            );
            if (!changed && !repositoryChanged) {
                finishRestoreBarrier();
                return;
            }
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                finishRestoreBarrier();
                if (error != null) {
                    DAI_Core.LOGGER.warn("<DAI>: Failed to restore resource packs after a cancelled world launch.", error);
                }
            }));
        });
    }


    /** Marks a resource restoration/reload that the shell must wait for. */
    public static synchronized void beginRestoreBarrier() {
        restoring = true;
    }

    /** Releases the shell after a resource restoration/reload finishes. */
    public static synchronized void finishRestoreBarrier() {
        restoring = false;
    }

    /**
     * Shell bootstrap guard for ordinary worlds. Experience unloads restore via
     * DAI_ExperienceResourcePackLifecycle; generic worlds restore here before
     * the shell starts so their temporary pack never appears on the DAI title.
     */
    public static synchronized boolean ensureRestoredBeforeShell() {
        if (restoring) return false;
        if (baselineEnabled == null) return true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return true;
        restoring = true;
        final int token = ++generation;
        minecraft.execute(() -> {
            boolean changed = restoreLiveSelectionNow(minecraft);
            boolean repositoryChanged = DAI_ResourcePackRepositorySync.syncFromOptions(
                    minecraft,
                    Set.of()
            );
            if (!changed && !repositoryChanged) {
                restoring = false;
                return;
            }
            minecraft.reloadResourcePacks().whenComplete((ignored, error) -> minecraft.execute(() -> {
                if (token != generation) return;
                restoring = false;
                if (error != null) {
                    DAI_Core.LOGGER.warn("<DAI>: Failed to reload resources while leaving a world-specific pack stack.", error);
                } else {
                    DAI_Core.LOGGER.info("<DAI>: World-specific resource packs are disabled before DAI shell bootstrap.");
                }
            }));
        });
        return false;
    }

    private static Set<String> read(Path saveRoot) {
        if (saveRoot == null) return Set.of();
        Path file = saveRoot.resolve(METADATA);
        if (!Files.isRegularFile(file)) return Set.of();
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("packs") || !json.get("packs").isJsonArray()) return Set.of();
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (JsonElement element : json.getAsJsonArray("packs")) {
                if (element != null && element.isJsonPrimitive()) {
                    String id = element.getAsString().trim();
                    if (!id.isBlank()) values.add(id);
                }
            }
            return Set.copyOf(values);
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read world resource-pack selection '{}'.", file, exception);
            return Set.of();
        }
    }

    private static boolean write(Path saveRoot, Set<String> packs) {
        try {
            Path file = saveRoot.resolve(METADATA);
            Files.createDirectories(file.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("schema", 1);
            JsonArray array = new JsonArray();
            for (String id : packs) array.add(id);
            json.add("packs", array);

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            DAI_Core.LOGGER.info(
                    "<DAI>: Saved {} auto-activate resource pack(s) for world '{}'.",
                    packs.size(), saveRoot.getFileName()
            );
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not save per-world resource-pack selection for '{}'.", saveRoot, exception);
            return false;
        }
    }

    private static boolean isResourcePackCandidate(Path path) {
        if (path == null) return false;
        if (Files.isDirectory(path)) return Files.isRegularFile(path.resolve("pack.mcmeta"));
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip");
    }

    private static String label(String value) {
        if (value == null || value.isBlank()) return "Resource Pack";
        String result = value;
        if (result.startsWith("file/")) result = result.substring(5);
        if (result.startsWith("dai_managed:")) result = result.substring("dai_managed:".length());
        if (result.toLowerCase(Locale.ROOT).endsWith(".zip")) result = result.substring(0, result.length() - 4);
        result = result.replace('_', ' ').replace('-', ' ').replace('/', ' ').trim();
        return result.isBlank() ? value : result;
    }

    private static Path savePath(String saveId) {
        if (saveId == null || saveId.isBlank()) return null;
        Path saves = gameDirectory().resolve("saves").normalize();
        Path save = saves.resolve(saveId).normalize();
        return save.startsWith(saves) ? save : null;
    }

    private static Path gameDirectory() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    }

    public record PackEntry(String id, String label, boolean selected) {}
}
