package io.github.j12h36h.dai.client.experience;

import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.packs.DAI_GlobalDatapackLibrary;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenDefinition;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Launches a complete JSON-authored experience directly from a title button.
 *
 * Existing saves are located by their DAI experience marker first, which means
 * Minecraft is free to disambiguate the original folder name. New saves use
 * Minecraft's own CreateWorldScreen and WorldCreationUiState; DAI configures
 * that state after the screen is initialized and can press Create on behalf of
 * the experience. Keeping the creation screen underneath this wrapper lets
 * Minecraft continue to own all validation, registries and save initialization.
 */
public final class DAI_ExperienceLauncher {

    private static PendingFresh pendingFresh;

    private DAI_ExperienceLauncher() {}

    /**
     * Starts a brand-new instance of an experience even when older matching
     * saves already exist. Minecraft remains responsible for choosing a
     * collision-safe save folder, so previous runs are never deleted here.
     */
    public static void launchNew(Screen parent, String experienceId) {
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();

        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null) {
            DAI_Core.LOGGER.error("<DAI>: Unknown experience '{}'.", experienceId);
            return;
        }
        if (!experience.createIfMissing()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' does not permit fresh-world creation.",
                    experience.id()
            );
            return;
        }

        Path sourcePack = findExperienceSourcePack(experience);
        DAI_ExperienceRuntime.prepare(experience, true, sourcePack);
        DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(experience.worldgen());

        if (worldgen != null) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Fresh experience '{}' requests worldgen '{}' using world preset '{}'.",
                    experience.id(), worldgen.id(), worldgen.worldPreset()
            );
        }

        if (openFresh(parent, experience, worldgen)) return;
        clearFreshLaunch();
        DAI_Core.LOGGER.error(
                "<DAI>: Could not open Minecraft's fresh-world flow for experience '{}'.",
                experience.id()
        );
    }


    /**
     * Boxhead/custom-experience creator entry point. Minecraft still owns the
     * actual CreateWorldScreen; this only supplies the user-facing name and a
     * selected DAI worldgen definition for the pending first-start handoff.
     */
    public static void launchNewConfigured(
            Screen parent,
            String experienceId,
            String worldName,
            String worldgenId
    ) {
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();
        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null || !experience.createIfMissing()) {
            DAI_Core.LOGGER.warn("<DAI>: Cannot create configured experience '{}'.", experienceId);
            return;
        }
        DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(worldgenId);
        if (worldgen == null) {
            DAI_Core.LOGGER.warn("<DAI>: Configured experience '{}' requested missing worldgen '{}'.", experienceId, worldgenId);
            return;
        }
        Path sourcePack = findExperienceSourcePack(experience);
        DAI_ExperienceRuntime.prepare(experience, true, sourcePack, worldgen.id());
        if (openFresh(parent, experience, worldgen, worldName)) return;
        clearFreshLaunch();
        DAI_Core.LOGGER.error("<DAI>: Could not open configured fresh-world flow for '{}'.", experience.id());
    }

    /**
     * Continues the most recently modified save belonging to this experience.
     * If no prior run exists, gracefully starts the first fresh run instead.
     */
    public static void continueLast(Screen parent, String experienceId) {
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();

        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null) {
            DAI_Core.LOGGER.error("<DAI>: Unknown experience '{}'.", experienceId);
            return;
        }

        Path save = findLatestExperienceSave(experience);
        boolean exists = save != null && Files.isRegularFile(save.resolve("level.dat"));
        if (!exists) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Experience '{}' has no prior save; Continue will start a new run.",
                    experience.id()
            );
            launchNew(parent, experienceId);
            return;
        }
        if (!experience.loadIfExisting()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' does not permit loading existing saves.",
                    experience.id()
            );
            return;
        }

        Path sourcePack = findExperienceSourcePack(experience);
        boolean firstJoin = markerRequiresFirstJoin(save);
        DAI_ExperienceRuntime.prepare(experience, firstJoin, sourcePack);

        if (openExisting(parent, save.getFileName().toString())) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Continuing latest experience '{}' save '{}'.",
                    experience.id(), save.getFileName()
            );
            return;
        }

        DAI_ExperienceLaunchState.clear();
        DAI_Core.LOGGER.error(
                "<DAI>: Could not open latest experience '{}' save '{}'.",
                experience.id(), save
        );
    }

    /**
     * Lists marker-verified saves belonging to an experience, newest first.
     * This powers JSON-authored title-screen save browsers without exposing
     * arbitrary Minecraft worlds to a datapack-defined UI.
     */
    public static List<ExperienceSave> listSaves(String experienceId) {
        DAI_ExperienceRepository.reload();
        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null) return List.of();

        List<Path> paths = findExperienceSaves(experience);
        List<ExperienceSave> output = new ArrayList<>(paths.size());
        for (Path path : paths) {
            String saveId = path.getFileName().toString();
            output.add(new ExperienceSave(
                    saveId,
                    sequenceNumber(saveId, experience),
                    saveModifiedTime(path)
            ));
        }
        return List.copyOf(output);
    }

    /** Opens one explicitly selected, marker-verified experience save. */
    public static void continueSave(Screen parent, String experienceId, String saveId) {
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();

        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null || saveId == null || saveId.isBlank()) {
            DAI_Core.LOGGER.warn("<DAI>: Could not continue explicit experience save: experience='{}' save='{}'.", experienceId, saveId);
            return;
        }

        Path save = savesDirectory().resolve(saveId).normalize();
        Path root = savesDirectory().normalize();
        if (!save.startsWith(root)
                || !Files.isDirectory(save)
                || !Files.isRegularFile(save.resolve("level.dat"))
                || !markerMatches(save, experience)) {
            DAI_Core.LOGGER.warn("<DAI>: Refused explicit experience save '{}' because it is missing or not owned by '{}'.", saveId, experience.id());
            return;
        }

        if (!experience.loadIfExisting()) {
            DAI_Core.LOGGER.warn("<DAI>: Experience '{}' does not permit loading existing saves.", experience.id());
            return;
        }

        Path sourcePack = findExperienceSourcePack(experience);
        DAI_ExperienceRuntime.prepare(experience, markerRequiresFirstJoin(save), sourcePack);
        if (openExisting(parent, saveId)) {
            DAI_Core.LOGGER.info("<DAI>: Continuing selected experience '{}' save '{}'.", experience.id(), saveId);
            return;
        }

        DAI_ExperienceLaunchState.clear();
        DAI_Core.LOGGER.error("<DAI>: Could not open selected experience '{}' save '{}'.", experience.id(), saveId);
    }

    /**
     * Permanently deletes one marker-verified save. The ownership check is
     * deliberately repeated here so a JSON title screen cannot delete an
     * unrelated Minecraft world by supplying its folder name.
     */
    public static boolean deleteSave(String experienceId, String saveId) {
        DAI_ExperienceRepository.reload();
        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null || saveId == null || saveId.isBlank()) return false;

        Path root = savesDirectory().normalize();
        Path save = root.resolve(saveId).normalize();
        if (!save.startsWith(root)
                || !Files.isDirectory(save)
                || !markerMatches(save, experience)) {
            DAI_Core.LOGGER.warn("<DAI>: Refused to delete save '{}' because it is not marker-owned by '{}'.", saveId, experience.id());
            return false;
        }

        try {
            List<Path> paths;
            try (var walk = Files.walk(save)) {
                paths = walk.sorted(Comparator.reverseOrder()).toList();
            }
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
            DAI_Core.LOGGER.info("<DAI>: Deleted experience '{}' save '{}'.", experience.id(), saveId);
            return true;
        } catch (Exception exception) {
            DAI_Core.LOGGER.error("<DAI>: Failed to delete experience '{}' save '{}'.", experience.id(), saveId, exception);
            return false;
        }
    }

    public record ExperienceSave(String saveId, int sequence, long modifiedTime) {
        public ExperienceSave {
            saveId = saveId == null ? "" : saveId;
            sequence = Math.max(1, sequence);
        }
    }

    public static void launch(Screen parent, String experienceId) {
        DAI_ExperienceRepository.reload();
        DAI_WorldgenRepository.reload();

        DAI_ExperienceDefinition experience = DAI_ExperienceRepository.get(experienceId);
        if (experience == null) {
            DAI_Core.LOGGER.error("<DAI>: Unknown experience '{}'.", experienceId);
            return;
        }

        Path save = findExperienceSave(experience);
        boolean exists = save != null && Files.isRegularFile(save.resolve("level.dat"));
        Path sourcePack = findExperienceSourcePack(experience);

        if (exists && experience.loadIfExisting()) {
            boolean firstJoin = markerRequiresFirstJoin(save);
            DAI_ExperienceRuntime.prepare(experience, firstJoin, sourcePack);
            if (openExisting(parent, save.getFileName().toString())) return;
            DAI_ExperienceLaunchState.clear();
            DAI_Core.LOGGER.error("<DAI>: Could not directly open experience save '{}'.", save);
            return;
        }

        if (!exists && experience.createIfMissing()) {
            DAI_ExperienceRuntime.prepare(experience, true, sourcePack);
            DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(experience.worldgen());
            if (worldgen != null) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience '{}' requests worldgen '{}' using world preset '{}'.",
                        experience.id(), worldgen.id(), worldgen.worldPreset()
                );
            }
            if (openFresh(parent, experience, worldgen)) return;
            clearFreshLaunch();
            DAI_Core.LOGGER.error("<DAI>: Could not open Minecraft's fresh-world flow for experience '{}'.", experience.id());
            return;
        }

        DAI_Core.LOGGER.warn(
                "<DAI>: Experience '{}' cannot launch: exists={} load_if_existing={} create_if_missing={}.",
                experience.id(), exists, experience.loadIfExisting(), experience.createIfMissing()
        );
    }

    /** Called from the post-client-tick hook while a fresh experience is being created. */
    public static void tickFreshLaunch() {
        PendingFresh pending = pendingFresh;
        if (pending == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();

        if (screen == null) return;

        if (!isCreateWorldScreen(screen)) {
            // Returning to the screen that launched the experience means the
            // player cancelled. Do not leave a first-join handoff armed.
            if (pending.screenSeen && screen == pending.parent) {
                clearFreshLaunch();
            }
            return;
        }

        pending.screenSeen = true;
        pending.ticksOnScreen++;

        // Give vanilla one full tick to initialize its world creation state.
        if (pending.ticksOnScreen < 2) return;

        if (!pending.configured) {
            pending.presetResolved = configureFreshScreen(screen, pending.experience, pending.worldgen, pending.worldName);
            pending.configured = true;
        }

        if (!pending.experience.autoCreate() || pending.createInvoked) return;

        if (!pending.presetResolved) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' requested world preset '{}', but that preset is not currently available "
                            + "to Minecraft's Create World registry. The configured Create World screen was left open instead of creating the wrong world.",
                    pending.experience.id(),
                    pending.worldgen == null ? "" : pending.worldgen.worldPreset()
            );
            pending.createInvoked = true;
            return;
        }

        if (invokeCreate(screen)) {
            pending.createInvoked = true;
            pendingFresh = null;
            DAI_Core.LOGGER.info("<DAI>: Creating new experience world '{}'.", pending.experience.id());
        } else {
            pending.createInvoked = true;
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not invoke Minecraft's Create action automatically for experience '{}'; the configured screen remains available.",
                    pending.experience.id()
            );
        }
    }

    public static boolean hasPendingFreshLaunch() {
        return pendingFresh != null;
    }

    private static boolean openExisting(Screen parent, String saveId) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Object flows = invokeNoArg(minecraft, "createWorldOpenFlows");
            if (flows == null) return false;

            for (Method method : flows.getClass().getMethods()) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("openworld") && !name.contains("loadworld")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, saveId, null);
                if (args == null) continue;
                try {
                    method.invoke(flows, args);
                    return true;
                } catch (Throwable exception) {
                    DAI_Core.debug("<DAI>: WorldOpenFlows method '{}' rejected experience launch.", method);
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn("<DAI>: Existing experience world launch failed for '{}'.", saveId, exception);
        }
        return false;
    }

    private static boolean openFresh(
            Screen parent,
            DAI_ExperienceDefinition experience,
            DAI_WorldgenDefinition worldgen
    ) {
        return openFresh(parent, experience, worldgen, experience == null ? "" : experience.saveName());
    }

    private static boolean openFresh(
            Screen parent,
            DAI_ExperienceDefinition experience,
            DAI_WorldgenDefinition worldgen,
            String requestedWorldName
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
            Runnable cancel = () -> {
                if (minecraft.player == null) minecraft.gui.setScreen(parent);
            };

            for (Method method : screenClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("openfresh") && !name.equals("open")) continue;
                Object[] args = resolve(method.getParameterTypes(), parent, minecraft, "", cancel);
                if (args == null) continue;
                if (!method.canAccess(null) && !method.trySetAccessible()) continue;
                try {
                    pendingFresh = new PendingFresh(parent, experience, worldgen, requestedWorldName);
                    Object result = method.invoke(null, args);
                    if (result instanceof Screen returned) minecraft.gui.setScreen(returned);
                    return true;
                } catch (Throwable ignored) {
                    pendingFresh = null;
                    // Try another mapped signature.
                }
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn("<DAI>: Fresh experience world screen could not be opened.", exception);
        }
        return false;
    }

    /**
     * Configures Minecraft's own WorldCreationUiState after CreateWorldScreen
     * has initialized. Returns false only when a requested world preset could
     * not be found; name/seed configuration is best-effort and non-destructive.
     */
    private static boolean configureFreshScreen(
            Screen screen,
            DAI_ExperienceDefinition experience,
            DAI_WorldgenDefinition worldgen,
            String requestedWorldName
    ) {
        if (screen == null || experience == null) return false;

        Object state = invokeNoArg(screen, "getUiState");
        if (state == null) {
            DAI_Core.LOGGER.warn("<DAI>: Create World state was unavailable for experience '{}'.", experience.id());
            return worldgen == null || worldgen.worldPreset().isBlank();
        }

        invokeCompatibleSetter(state, "setName", requestedWorldName == null || requestedWorldName.isBlank() ? experience.saveName() : requestedWorldName.trim());

        if (worldgen != null && worldgen.seed() != null) {
            // Minecraft's current UI state accepts the seed as its editable
            // string form; the compatibility setter also handles numeric maps.
            if (!invokeCompatibleSetter(state, "setSeed", Long.toString(worldgen.seed()))) {
                invokeCompatibleSetter(state, "setSeed", worldgen.seed());
            }
        }

        if (worldgen == null || worldgen.worldPreset().isBlank()) return true;

        Object worldType = findWorldTypeEntry(state, worldgen.worldPreset());
        if (worldType == null) return false;

        return invokeCompatibleSetter(state, "setWorldType", worldType)
                || invokeCompatibleSetter(state, "setWorldPreset", worldType)
                || invokeCompatibleSetter(state, "setPreset", worldType);
    }

    private static Object findWorldTypeEntry(Object state, String requestedPreset) {
        String requested = normalizeId(requestedPreset);
        List<Object> candidates = new ArrayList<>();
        collectIterable(invokeNoArg(state, "getNormalPresetList"), candidates);
        collectIterable(invokeNoArg(state, "getAltPresetList"), candidates);

        // Mapped builds can expose a single combined list instead.
        if (candidates.isEmpty()) {
            collectIterable(invokeNoArg(state, "getPresetList"), candidates);
            collectIterable(invokeNoArg(state, "getWorldTypeList"), candidates);
        }

        for (Object entry : candidates) {
            if (entry == null) continue;
            String id = worldTypeId(entry);
            if (requested.equals(normalizeId(id))) return entry;
        }
        return null;
    }

    private static String worldTypeId(Object entry) {
        Object preset = invokeNoArg(entry, "preset");
        if (preset == null) preset = invokeNoArg(entry, "worldPreset");
        if (preset == null) return "";

        Object key = unwrapOptional(invokeNoArg(preset, "unwrapKey"));
        if (key == null) key = unwrapOptional(invokeNoArg(preset, "getKey"));
        if (key == null) key = preset;

        Object id = invokeNoArg(key, "identifier");
        if (id == null) id = invokeNoArg(key, "location");
        if (id == null) id = invokeNoArg(key, "id");
        return id == null ? "" : String.valueOf(id);
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private static void collectIterable(Object source, List<Object> output) {
        if (source instanceof Iterable<?> iterable) {
            for (Object value : iterable) output.add(value);
        } else if (source != null && source.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(source);
            for (int i = 0; i < length; i++) output.add(java.lang.reflect.Array.get(source, i));
        }
    }

    private static boolean invokeCreate(Screen screen) {
        for (Method method : screen.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 0) continue;
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals("oncreate") && !name.equals("createworld") && !name.equals("create")) continue;
            try {
                if (!method.canAccess(screen) && !method.trySetAccessible()) continue;
                method.invoke(screen);
                return true;
            } catch (Throwable exception) {
                DAI_Core.LOGGER.warn("<DAI>: Minecraft rejected automatic experience world creation.", exception);
                return false;
            }
        }
        return false;
    }

    private static boolean invokeCompatibleSetter(Object target, String preferredName, Object value) {
        if (target == null || value == null) return false;
        String preferred = preferredName.toLowerCase(Locale.ROOT);

        for (Method method : target.getClass().getMethods()) {
            if (method.getParameterCount() != 1) continue;
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals(preferred)) continue;
            Object converted = convertValue(method.getParameterTypes()[0], value);
            if (converted == UNRESOLVED) continue;
            try {
                method.invoke(target, converted);
                return true;
            } catch (Throwable ignored) { }
        }

        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 1) continue;
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.equals(preferred)) continue;
            Object converted = convertValue(method.getParameterTypes()[0], value);
            if (converted == UNRESOLVED) continue;
            try {
                if (!method.canAccess(target) && !method.trySetAccessible()) continue;
                method.invoke(target, converted);
                return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static final Object UNRESOLVED = new Object();

    private static Object convertValue(Class<?> parameter, Object value) {
        if (parameter.isInstance(value)) return value;
        if (parameter == String.class) return String.valueOf(value);
        if ((parameter == long.class || parameter == Long.class) && value instanceof Number number) return number.longValue();
        if ((parameter == int.class || parameter == Integer.class) && value instanceof Number number) return number.intValue();
        if ((parameter == float.class || parameter == Float.class) && value instanceof Number number) return number.floatValue();
        if ((parameter == double.class || parameter == Double.class) && value instanceof Number number) return number.doubleValue();
        return UNRESOLVED;
    }

    private static Object[] resolve(
            Class<?>[] types,
            Screen parent,
            Minecraft minecraft,
            String saveId,
            Runnable runnable
    ) {
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (Screen.class.isAssignableFrom(type)) values[i] = parent;
            else if (Minecraft.class.isAssignableFrom(type)) values[i] = minecraft;
            else if (type == String.class) values[i] = saveId;
            else if (type == boolean.class || type == Boolean.class) values[i] = false;
            else if (Runnable.class.isAssignableFrom(type)) values[i] = runnable == null ? (Runnable) () -> {} : runnable;
            else return null;
        }
        return values;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
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

    private static boolean isCreateWorldScreen(Screen screen) {
        return screen != null && screen.getClass().getName().equals(
                "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"
        );
    }

    private static Path findLatestExperienceSave(DAI_ExperienceDefinition experience) {
        List<Path> candidates = new ArrayList<>(findExperienceSaves(experience));

        // Preserve the pre-browser compatibility path for interrupted/legacy
        // preferred saves that were created before DAI wrote experience markers.
        Path preferred = savesDirectory().resolve(sanitizeSaveId(experience.saveId()));
        if (Files.isRegularFile(preferred.resolve("level.dat")) && !candidates.contains(preferred)) {
            candidates.add(preferred);
        }

        candidates.sort(Comparator
                .comparingLong(DAI_ExperienceLauncher::saveModifiedTime)
                .reversed()
                .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<Path> findExperienceSaves(DAI_ExperienceDefinition experience) {
        Path saves = savesDirectory();
        if (!Files.isDirectory(saves) || experience == null) return List.of();

        List<Path> candidates = new ArrayList<>();
        try (var stream = Files.list(saves)) {
            stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .filter(path -> markerMatches(path, experience))
                    .forEach(candidates::add);
        } catch (Exception exception) {
            DAI_Core.debug("<DAI>: Could not scan experience save markers: {}", exception.toString());
        }

        candidates.sort(Comparator
                .comparingLong(DAI_ExperienceLauncher::saveModifiedTime)
                .reversed()
                .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(candidates);
    }

    private static long saveModifiedTime(Path save) {
        if (save == null) return Long.MIN_VALUE;
        Path marker = save.resolve("dai").resolve("experience.json");
        Path level = save.resolve("level.dat");
        long modified = Long.MIN_VALUE;
        try {
            if (Files.isRegularFile(marker)) {
                modified = Math.max(modified, Files.getLastModifiedTime(marker).toMillis());
            }
            if (Files.isRegularFile(level)) {
                modified = Math.max(modified, Files.getLastModifiedTime(level).toMillis());
            }
            if (Files.isDirectory(save)) {
                modified = Math.max(modified, Files.getLastModifiedTime(save).toMillis());
            }
        } catch (Exception ignored) {
            // Preserve the best timestamp obtained so far.
        }
        return modified;
    }

    private static Path findExperienceSave(DAI_ExperienceDefinition experience) {
        Path saves = savesDirectory();
        Path preferred = saves.resolve(sanitizeSaveId(experience.saveId()));
        if (Files.isRegularFile(preferred.resolve("level.dat"))) return preferred;
        if (!Files.isDirectory(saves)) return null;

        try (var stream = Files.list(saves)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                    .filter(path -> markerMatches(path, experience))
                    .findFirst()
                    .orElse(null);
        } catch (Exception exception) {
            DAI_Core.debug("<DAI>: Could not scan experience save markers: {}", exception.toString());
            return null;
        }
    }


    private static boolean markerRequiresFirstJoin(Path save) {
        if (save == null) return false;
        Path marker = save.resolve("dai").resolve("experience.json");
        if (!Files.isRegularFile(marker)) return false;
        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(marker, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            // A first join is complete only after BOTH server world bootstrap
            // and client startup-action dispatch reached the handoff point.
            // Older markers lack startup_dispatched; intentionally treat them
            // as incomplete once so interrupted/restart-gated test saves repair
            // themselves on the next Start Journey press.
            return !root.has("first_join_complete")
                    || !root.get("first_join_complete").getAsBoolean()
                    || !root.has("startup_dispatched")
                    || !root.get("startup_dispatched").getAsBoolean()
                    // v4 could mark startup_dispatched before Minecraft had
                    // actually accepted the first lifecycle command. Require
                    // the v5 handoff marker once so affected Journey saves rerun
                    // their true first-join initialization after pack selection
                    // is repaired.
                    || !root.has("handoff_version")
                    || root.get("handoff_version").getAsInt() < 2;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Path findExperienceSourcePack(DAI_ExperienceDefinition experience) {
        if (experience == null || experience.id().isBlank()) return null;

        String normalized = normalizeId(experience.id());
        int split = normalized.indexOf(':');
        if (split <= 0 || split >= normalized.length() - 1) return null;

        String namespace = normalized.substring(0, split);
        String path = normalized.substring(split + 1);
        String entry = "data/" + namespace + "/" + DAI_ExperienceRepository.DIRECTORY + "/" + path + ".json";

        Path game = savesDirectory().getParent();
        Path selected = null;

        // A MAIN experience may be distributed directly inside another mod
        // JAR. Treat that archive as the source owner for launch bookkeeping;
        // the server runtime knows not to copy a mod JAR into world/datapacks.
        Path embeddedMod = findPackContaining(game.resolve("mods"), entry);
        if (embeddedMod != null) selected = embeddedMod;

        Path saves = game.resolve("saves");
        if (Files.isDirectory(saves)) {
            try (var worlds = Files.list(saves)) {
                for (Path world : worlds.filter(Files::isDirectory).sorted().toList()) {
                    Path found = findPackContaining(world.resolve("datapacks"), entry);
                    if (found != null) selected = found;
                }
            } catch (Exception exception) {
                DAI_Core.debug("<DAI>: Could not resolve experience source from world datapacks: {}", exception.toString());
            }
        }

        Path global = findPackContaining(DAI_GlobalDatapackLibrary.initialize(), entry);
        if (global != null) selected = global;

        if (selected != null) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Experience '{}' source datapack resolved to '{}'.",
                    experience.id(), selected
            );
        } else {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' was discovered, but its source datapack could not be resolved for world handoff.",
                    experience.id()
            );
        }
        return selected;
    }

    private static Path findPackContaining(Path datapacks, String entry) {
        if (!Files.isDirectory(datapacks)) return null;
        Path selected = null;
        try (var packs = Files.list(datapacks)) {
            for (Path pack : packs.sorted().toList()) {
                // Experience definitions are owned only by MAIN packs. This
                // prevents an addon that happens to contain the same resource
                // path from becoming the world-handoff source by accident.
                if (!DAI_DatapackMetadata.isMain(pack)) continue;

                if (Files.isDirectory(pack)) {
                    if (Files.isRegularFile(pack.resolve(entry.replace('/', java.io.File.separatorChar)))) {
                        selected = pack;
                    }
                    continue;
                }

                String name = pack.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".zip") && !name.endsWith(".jar")) continue;
                try (ZipFile zip = new ZipFile(pack.toFile())) {
                    if (zip.getEntry(entry) != null) selected = pack;
                } catch (Exception ignored) {
                    // A malformed archive is ignored here; the normal pack
                    // loader will report it if the user tries to activate it.
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return selected;
    }

    private static boolean markerMatches(Path save, DAI_ExperienceDefinition experience) {
        Path marker = save.resolve("dai").resolve("experience.json");
        if (!Files.isRegularFile(marker)) return false;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(marker, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = root.has("experience") ? root.get("experience").getAsString() : "";
            String saveId = root.has("save_id") ? root.get("save_id").getAsString() : "";
            return normalizeId(id).equals(normalizeId(experience.id()))
                    || (!saveId.isBlank() && saveId.equalsIgnoreCase(experience.saveId()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int sequenceNumber(String saveId, DAI_ExperienceDefinition experience) {
        String base = sanitizeSaveId(experience == null ? "" : experience.saveId());
        if (saveId == null || saveId.isBlank() || saveId.equalsIgnoreCase(base)) return 1;

        Pattern parenthetical = Pattern.compile(
                "^" + Pattern.quote(base) + "\\s*\\((\\d+)\\)$",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = parenthetical.matcher(saveId);
        if (matcher.matches()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(1)) + 1);
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }

        Pattern numeric = Pattern.compile(
                "^" + Pattern.quote(base) + "[-_ ]+(\\d+)$",
                Pattern.CASE_INSENSITIVE
        );
        matcher = numeric.matcher(saveId);
        if (matcher.matches()) {
            try {
                return Math.max(1, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }

        return Math.max(1, Math.abs(saveId.toLowerCase(Locale.ROOT).hashCode() % 999) + 1);
    }

    private static void clearFreshLaunch() {
        pendingFresh = null;
        DAI_ExperienceLaunchState.clear();
    }

    private static Path savesDirectory() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path game = config.getParent() == null ? Path.of(".").toAbsolutePath().normalize() : config.getParent();
        return game.resolve("saves");
    }

    private static String sanitizeSaveId(String raw) {
        String value = raw == null || raw.isBlank() ? "DAI Experience" : raw.trim();
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class PendingFresh {
        private final Screen parent;
        private final DAI_ExperienceDefinition experience;
        private final DAI_WorldgenDefinition worldgen;
        private final String worldName;
        private int ticksOnScreen;
        private boolean screenSeen;
        private boolean configured;
        private boolean presetResolved = true;
        private boolean createInvoked;

        private PendingFresh(
                Screen parent,
                DAI_ExperienceDefinition experience,
                DAI_WorldgenDefinition worldgen,
                String worldName
        ) {
            this.parent = parent;
            this.experience = experience;
            this.worldgen = worldgen;
            this.worldName = worldName == null ? "" : worldName.trim();
        }
    }
}
