package io.github.j12h36h.dai.client.play;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.packs.DAI_WorldResourcePackSelection;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import io.github.j12h36h.dai.gamerules.DAI_WorldGameRuleOverrides;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.packs.DAI_DatapackRole;
import io.github.j12h36h.dai.packs.DAI_WorldAddonSelection;
import io.github.j12h36h.dai.runtime.DAI_StandaloneLaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Save discovery, launch and per-world management used by DAI's Play screens. */
public final class DAI_PlayWorldAccess {

    private static final String SHELL_MARKER = "dai/shell.json";
    private static final String SHELL_SAVE_PREFIX = "DAI_Engine_Shell";

    private static boolean managedOpenPending;
    private static Object managedSourceLevel;
    private static String managedSaveId = "";
    private static Screen lastManagedPrompt;
    private static int managedPromptTicks;
    private static int managedOpenTicks;
    private static boolean managedFallbackRevealed;

    private DAI_PlayWorldAccess() {}

    public static List<WorldEntry> worlds() {
        Path root = savesDirectory();
        if (!Files.isDirectory(root)) return List.of();
        ArrayList<WorldEntry> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            for (Path path : stream.filter(Files::isDirectory).toList()) {
                if (!Files.isRegularFile(path.resolve("level.dat"))) continue;
                if (Files.isRegularFile(path.resolve(SHELL_MARKER))) continue;
                String id = path.getFileName().toString();
                if (isInternalShellSave(id)) continue;
                result.add(new WorldEntry(id, modified(path.resolve("level.dat"))));
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not enumerate singleplayer saves.", exception);
        }
        result.sort(Comparator.comparingLong(WorldEntry::modifiedMillis).reversed());
        return List.copyOf(result);
    }

    public static WorldEntry mostRecent() {
        List<WorldEntry> worlds = worlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    /** User-facing level name from level.dat; the save folder id remains stable. */
    public static String displayName(WorldEntry world) {
        if (world == null) return "WORLD";
        Path root = worldPath(world.saveId());
        return DAI_LevelDatEditor.levelName(root, display(world.saveId()));
    }

    /** Changes the Minecraft LevelName without renaming the save folder or experience identity. */
    public static boolean rename(WorldEntry world, String newName) {
        if (world == null || world.saveId().isBlank() || isInternalShellSave(world.saveId())) return false;
        Path root = worldPath(world.saveId());
        boolean written = DAI_LevelDatEditor.rename(root, newName);
        if (written) {
            DAI_Core.LOGGER.info("<DAI>: Renamed local world '{}' to '{}'.", world.saveId(), newName == null ? "" : newName.trim());
        }
        return written;
    }

    /**
     * Complete dynamic game-rule catalog for this save. Minecraft 26.2 keeps
     * rules in SavedData rather than level.dat, so DAI exposes every registered
     * rule with its vanilla/modded default and overlays offline Modify World
     * overrides from dai/game_rules.json.
     */
    public static List<DAI_WorldGameRuleOverrides.RuleEntry> gameRuleEntries(WorldEntry world) {
        if (world == null) return List.of();
        return DAI_WorldGameRuleOverrides.snapshot(worldPath(world.saveId()));
    }

    /** Backwards-compatible map view used by callers that only need id/value. */
    public static Map<String, String> gameRules(WorldEntry world) {
        if (world == null) return Map.of();
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (DAI_WorldGameRuleOverrides.RuleEntry entry : gameRuleEntries(world)) {
            values.put(entry.id(), entry.value());
        }
        return Map.copyOf(values);
    }

    /** Stores one validated offline override; Minecraft applies it at server start. */
    public static boolean setGameRule(WorldEntry world, String rule, String value) {
        if (world == null || world.saveId().isBlank() || isInternalShellSave(world.saveId())) return false;
        boolean written = DAI_WorldGameRuleOverrides.set(worldPath(world.saveId()), rule, value);
        if (written) {
            DAI_Core.LOGGER.info("<DAI>: World '{}' game rule override '{}' -> '{}'.", world.saveId(), rule, value);
        }
        return written;
    }

    /** Removes a DAI override so the registered Minecraft default is used again. */
    public static boolean resetGameRule(WorldEntry world, String rule) {
        if (world == null || world.saveId().isBlank() || isInternalShellSave(world.saveId())) return false;
        boolean written = DAI_WorldGameRuleOverrides.reset(worldPath(world.saveId()), rule);
        if (written) {
            DAI_Core.LOGGER.info("<DAI>: Reset world '{}' game rule '{}' to its registered default.", world.saveId(), rule);
        }
        return written;
    }

    public static boolean open(Screen parent, WorldEntry world) {
        if (world == null || world.saveId().isBlank()) return false;

        String experienceId = experienceId(world.saveId());
        if (!experienceId.isBlank()) {
            DAI_ExperienceRepository.reload();
            DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
            if (experience != null && experience.loadIfExisting()) {
                clearManagedOpen();
                DAI_Core.LOGGER.info(
                        "<DAI>: Routing experience-owned save '{}' through experience '{}' from the DAI Play UI.",
                        world.saveId(), experience.id()
                );
                DAI_ExperienceLauncher.continueSave(parent, experience.id(), world.saveId());
                return true;
            }
            DAI_Core.LOGGER.warn(
                    "<DAI>: Save '{}' declares experience '{}', but that experience is not currently loadable; falling back to generic DAI world open.",
                    world.saveId(), experienceId
            );
        }

        return DAI_ShellWorldRuntime.runAfterCleanWorldDetach(
                "LOADING " + display(world.saveId()),
                parent,
                () -> DAI_WorldResourcePackSelection.applyBeforeWorldLaunch(world.saveId(), () -> {
                    if (!openDetached(parent, world)) {
                        DAI_WorldResourcePackSelection.cancelLaunchAndRestore();
                        DAI_Core.LOGGER.error(
                                "<DAI>: Minecraft world-open flow was unavailable for '{}'.",
                                world.saveId()
                        );
                    }
                })
        );
    }

    private static boolean openDetached(Screen parent, WorldEntry world) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || world == null) return false;

        /*
         * Preserve the world's existing addon state only after the shell has
         * detached. For a pure-vanilla save this arms an explicit empty
         * selection before the target PackRepository is constructed.
         */
        armStandaloneSelectionForOpen(world);
        armManagedOpen(minecraft, world.saveId());
        DAI_ShellWorldRuntime.prepareExperienceTransition("LOADING " + display(world.saveId()));

        try {
            Object flows = invokeNoArg(minecraft, "createWorldOpenFlows");
            if (flows == null) throw new IllegalStateException("createWorldOpenFlows unavailable");
            for (Method method : flows.getClass().getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("openworld") && !name.contains("loadworld")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, world.saveId());
                if (args == null) continue;
                try {
                    method.invoke(flows, args);
                    return true;
                } catch (Throwable ignored) {
                    // Probe another mapped overload.
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not open world '{}'.", world.saveId(), exception);
        }

        clearManagedOpen();
        DAI_StandaloneLaunchState.clear();
        DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
        return false;
    }

    public static void tickWorldOpen() {
        if (!managedOpenPending) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            clearManagedOpen();
            return;
        }

        managedOpenTicks++;
        Screen screen = minecraft.gui == null ? null : minecraft.gui.screen();
        boolean destinationAttached = minecraft.level != null
                && minecraft.player != null
                && minecraft.level != managedSourceLevel;
        if (destinationAttached) {
            DAI_Core.LOGGER.info(
                    "<DAI>: DAI Play world '{}' attached successfully after {} client ticks.",
                    managedSaveId, managedOpenTicks
            );
            clearManagedOpen();
            return;
        }

        if (DAI_WorldLaunchConfirmation.isBackupPrompt(screen)) {
            if (screen != lastManagedPrompt) {
                lastManagedPrompt = screen;
                managedPromptTicks = 0;
            }
            managedPromptTicks++;
            if (managedPromptTicks == 1 || managedPromptTicks % 10 == 0) {
                if (DAI_WorldLaunchConfirmation.acceptAffirmative(screen)) {
                    DAI_Core.LOGGER.info(
                            "<DAI>: Auto-accepted DAI Play backup confirmation '{}' for save '{}'.",
                            screen.getClass().getSimpleName(), managedSaveId
                    );
                    managedPromptTicks = -20;
                } else if (managedPromptTicks == 1) {
                    DAI_Core.LOGGER.warn(
                            "<DAI>: DAI Play detected backup confirmation '{}' for save '{}', but it was not invokable yet; retrying.",
                            screen.getClass().getSimpleName(), managedSaveId
                    );
                }
            }
            if (!managedFallbackRevealed && managedPromptTicks >= 120) {
                managedFallbackRevealed = true;
                DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
                DAI_Core.LOGGER.warn(
                        "<DAI>: DAI Play backup confirmation for save '{}' remained blocked; revealing Minecraft's screen for manual input.",
                        managedSaveId
                );
            }
            return;
        }

        if (DAI_WorldLaunchConfirmation.isGenericPrompt(screen)) {
            if (screen != lastManagedPrompt) {
                lastManagedPrompt = screen;
                DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
                DAI_Core.LOGGER.warn(
                        "<DAI>: DAI Play world '{}' reached generic confirmation '{}'; leaving it untouched.",
                        managedSaveId,
                        screen.getClass().getName()
                );
            }
            return;
        }

        lastManagedPrompt = null;
        managedPromptTicks = 0;
        if (managedOpenTicks % 100 == 0) {
            DAI_Core.LOGGER.info(
                    "<DAI>: DAI Play world '{}' still opening ({} ticks, screen='{}', sourceLevelSame={}).",
                    managedSaveId,
                    managedOpenTicks,
                    screen == null ? "<none>" : screen.getClass().getName(),
                    minecraft.level == managedSourceLevel
            );
        }

        if (!managedFallbackRevealed
                && managedOpenTicks >= 240
                && !isRecognizedWorldLoadingScreen(screen)) {
            managedFallbackRevealed = true;
            DAI_ShellWorldRuntime.resumeAfterCancelledTransition();
            DAI_Core.LOGGER.warn(
                    "<DAI>: DAI Play world '{}' has not detached from its source world after {} ticks (screen='{}'); dropping the Safe Loading Veil so the real blocker is visible.",
                    managedSaveId,
                    managedOpenTicks,
                    screen == null ? "<none>" : screen.getClass().getName()
            );
        }
    }

    public static WorldSettings settings(WorldEntry world) {
        if (world == null) return new WorldSettings("", "ADDONS: AVAILABLE");
        String experienceId = experienceId(world.saveId());
        if (experienceId.isBlank()) return new WorldSettings("", "ADDONS: SELECT PER WORLD");

        DAI_ExperienceRepository.reload();
        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null) return new WorldSettings(experienceId, "EXPERIENCE ADDON POLICY UNAVAILABLE");
        DAI_ExperienceDefinition.AddonPolicy policy = experience.addons();
        if (!policy.enabled()) return new WorldSettings(experienceId, "ADDONS: DISABLED BY EXPERIENCE");
        if (!policy.whitelist().isEmpty()) return new WorldSettings(experienceId, "ADDONS: EXPERIENCE WHITELIST");
        return new WorldSettings(experienceId, "ADDONS: ALLOWED");
    }

    public static List<AddonEntry> addons(WorldEntry world) {
        if (world == null) return List.of();
        Set<String> selected = currentSelectedStableIds(world);
        String experienceId = experienceId(world.saveId());
        DAI_ExperienceDefinition.AddonPolicy policy = DAI_ExperienceDefinition.AddonPolicy.DEFAULT;
        if (!experienceId.isBlank()) {
            DAI_ExperienceRepository.reload();
            DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
            if (experience != null) policy = experience.addons();
        }

        List<AddonEntry> result = new ArrayList<>();
        for (Path addon : DAI_DatapackMetadata.globalAddons()) {
            String stableId = DAI_DatapackMetadata.stableId(addon);
            if (stableId.isBlank()) continue;
            boolean allowed = experienceId.isBlank() || policy.allows(stableId);
            result.add(new AddonEntry(
                    stableId,
                    addonLabel(addon),
                    selected.contains(DAI_WorldAddonSelection.normalize(stableId)),
                    allowed
            ));
        }
        result.sort(Comparator.comparing(AddonEntry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static boolean setAddonSelected(WorldEntry world, String stableId, boolean selected) {
        if (world == null || stableId == null || stableId.isBlank()) return false;
        Path root = worldPath(world.saveId());
        if (root == null) return false;

        WorldSettings settings = settings(world);
        if (!settings.experienceId().isBlank()) {
            DAI_ExperienceRepository.reload();
            DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(settings.experienceId());
            if (experience == null || !experience.addons().allows(stableId)) return false;
        }

        LinkedHashSet<String> values = new LinkedHashSet<>(currentSelectedStableIds(world));
        String normalized = DAI_WorldAddonSelection.normalize(stableId);
        if (selected) values.add(normalized);
        else values.remove(normalized);
        boolean written = DAI_WorldAddonSelection.write(root, values);
        if (written) {
            DAI_Core.LOGGER.info(
                    "<DAI>: World '{}' ADDON '{}' selected={} ({} total selected).",
                    world.saveId(), normalized, selected, values.size()
            );
        }
        return written;
    }

    public static boolean delete(WorldEntry world) {
        if (world == null || world.saveId().isBlank() || isInternalShellSave(world.saveId())) return false;
        Path root = worldPath(world.saveId());
        if (root == null || !Files.isDirectory(root)) return false;

        try {
            List<Path> paths;
            try (var walk = Files.walk(root)) {
                paths = walk.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) Files.deleteIfExists(path);
            DAI_Core.LOGGER.info("<DAI>: Deleted local world '{}'.", world.saveId());
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not delete local world '{}'.", world.saveId(), exception);
            return false;
        }
    }

    public static String experienceId(String saveId) {
        Path save = worldPath(saveId);
        if (save == null) return "";
        Path marker = save.resolve("dai").resolve("experience.json");
        if (!Files.isRegularFile(marker)) return "";
        try {
            JsonObject json = JsonParser.parseString(Files.readString(marker, StandardCharsets.UTF_8)).getAsJsonObject();
            return json.has("experience") ? json.get("experience").getAsString().trim() : "";
        } catch (Exception exception) {
            DAI_Core.debug("<DAI>: Could not read experience marker for Play world '{}': {}", saveId, exception.toString());
            return "";
        }
    }

    private static Set<String> currentSelectedStableIds(WorldEntry world) {
        Path root = worldPath(world == null ? "" : world.saveId());
        if (root == null) return Set.of();
        var persisted = DAI_WorldAddonSelection.read(root);
        if (persisted.isPresent()) return persisted.get().stableIds();

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        Path datapacks = root.resolve("datapacks");
        if (!Files.isDirectory(datapacks)) return Set.of();
        try (var stream = Files.list(datapacks)) {
            for (Path pack : stream.toList()) {
                if (DAI_DatapackMetadata.role(pack) != DAI_DatapackRole.ADDON) continue;
                String id = DAI_WorldAddonSelection.normalize(DAI_DatapackMetadata.stableId(pack));
                if (!id.isBlank()) selected.add(id);
            }
        } catch (Exception exception) {
            DAI_Core.debug("<DAI>: Could not inspect world ADDONs for '{}': {}", world.saveId(), exception.toString());
        }
        return Set.copyOf(selected);
    }

    private static void armStandaloneSelectionForOpen(WorldEntry world) {
        Path root = worldPath(world == null ? "" : world.saveId());
        if (root == null) return;

        var persisted = DAI_WorldAddonSelection.read(root);
        if (persisted.isPresent()) {
            // An explicit empty per-world marker means this save is pure
            // vanilla. Re-arm the empty one-shot state so generated DAI
            // registry data is also excluded while Minecraft builds the
            // repository for this open. Non-empty selections are read from
            // disk by the server after login and do not need a filename handoff.
            if (persisted.get().stableIds().isEmpty()) {
                DAI_StandaloneLaunchState.prepare(Set.of());
            } else {
                DAI_StandaloneLaunchState.clear();
            }
            return;
        }

        LinkedHashSet<String> stableIds = new LinkedHashSet<>();
        Path datapacks = root.resolve("datapacks");
        if (Files.isDirectory(datapacks)) {
            try (var stream = Files.list(datapacks)) {
                for (Path pack : stream.toList()) {
                    if (DAI_DatapackMetadata.role(pack) != DAI_DatapackRole.ADDON) continue;
                    String stableId = DAI_WorldAddonSelection.normalize(DAI_DatapackMetadata.stableId(pack));
                    if (!stableId.isBlank()) stableIds.add(stableId);
                }
            } catch (Exception exception) {
                DAI_Core.debug("<DAI>: Could not snapshot existing ADDON selection for '{}': {}", world.saveId(), exception.toString());
            }
        }
        // Persist the snapshot before launch so an unmarked vanilla save with
        // zero DAI addons remains zero-addons instead of inheriting globals.
        DAI_WorldAddonSelection.write(root, stableIds);
        if (stableIds.isEmpty()) {
            DAI_StandaloneLaunchState.prepare(Set.of());
        } else {
            DAI_StandaloneLaunchState.clear();
        }
    }

    private static void armManagedOpen(Minecraft minecraft, String saveId) {
        managedOpenPending = true;
        managedSourceLevel = minecraft == null ? null : minecraft.level;
        managedSaveId = saveId == null ? "" : saveId;
        lastManagedPrompt = null;
        managedPromptTicks = 0;
        managedOpenTicks = 0;
        managedFallbackRevealed = false;
    }

    private static void clearManagedOpen() {
        managedOpenPending = false;
        managedSourceLevel = null;
        managedSaveId = "";
        lastManagedPrompt = null;
        managedPromptTicks = 0;
        managedOpenTicks = 0;
        managedFallbackRevealed = false;
    }

    private static boolean isRecognizedWorldLoadingScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getSimpleName();
        return "LevelLoadingScreen".equals(name)
                || "ReceivingLevelScreen".equals(name)
                || "ProgressScreen".equals(name)
                || "GenericWaitingScreen".equals(name)
                || "GenericMessageScreen".equals(name);
    }

    public static String display(String saveId) {
        if (saveId == null || saveId.isBlank()) return "WORLD";
        String value = saveId.replace('_', ' ').replace('-', ' ').trim();
        return value.isBlank() ? "WORLD" : value;
    }

    public static boolean isInternalShellSave(String saveId) {
        if (saveId == null || saveId.isBlank()) return false;
        return saveId.regionMatches(true, 0, SHELL_SAVE_PREFIX, 0, SHELL_SAVE_PREFIX.length());
    }

    private static String addonLabel(Path path) {
        if (path == null || path.getFileName() == null) return "DAI Addon";
        String value = path.getFileName().toString();
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        return value.replace('_', ' ').replace('-', ' ').trim();
    }

    private static Path worldPath(String saveId) {
        if (saveId == null || saveId.isBlank()) return null;
        Path root = savesDirectory().normalize();
        Path save = root.resolve(saveId).normalize();
        return save.startsWith(root) ? save : null;
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    private static Path savesDirectory() {
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        return game.resolve("saves");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) { }
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object[] resolve(Class<?>[] types, Screen parent, Minecraft minecraft, String saveId) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (Screen.class.isAssignableFrom(type)) values[i] = parent;
            else if (Minecraft.class.isAssignableFrom(type)) values[i] = minecraft;
            else if (type == String.class) values[i] = saveId;
            else if (type == boolean.class || type == Boolean.class) values[i] = false;
            else if (Runnable.class.isAssignableFrom(type)) values[i] = (Runnable) () -> {};
            else return null;
        }
        return values;
    }

    public record WorldEntry(String saveId, long modifiedMillis) {}
    public record WorldSettings(String experienceId, String policyText) {}
    public record AddonEntry(String stableId, String label, boolean selected, boolean allowed) {}
}
