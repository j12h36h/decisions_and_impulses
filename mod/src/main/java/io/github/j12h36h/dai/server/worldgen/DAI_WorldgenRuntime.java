package io.github.j12h36h.dai.server.worldgen;

import io.github.j12h36h.dai.worldgen.DAI_WorldgenDefinition;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.packs.DAI_DatapackMetadata;
import io.github.j12h36h.dai.packs.DAI_DatapackRole;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Applies high-level DAI worldgen bootstrap data when an experience world starts. */
public final class DAI_WorldgenRuntime {

    private static volatile boolean firstStartScheduled;
    private static volatile Path currentWorldRoot;
    private static volatile DAI_ExperienceDefinition currentExperience;

    private DAI_WorldgenRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_WorldgenRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DAI_WorldgenRuntime::onPlayerLoggedIn);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path root = server.getWorldPath(LevelResource.ROOT);
        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();

        if (pending == null) {
            // Global ADDON packs are useful outside a MAIN experience too.
            // Standalone worlds therefore receive the same globally installed
            // addon library, but without applying MAIN exclusivity or touching
            // any datapack the world already selected itself.
            installStandaloneAddonStack(server, root);
            return;
        }

        DAI_ExperienceDefinition experience = pending.definition();
        currentWorldRoot = root;
        currentExperience = experience;
        firstStartScheduled = false;

        // A launched DAI save owns exactly one MAIN experience datapack but may
        // layer any number of ADDON datapacks from DAI's global library. Copy /
        // update the full stack first, then perform one combined server reload.
        CompletableFuture<?> packReload = installExperienceStack(server, root, pending.sourcePack());
        DAI_ExperienceLaunchState.setPackReloadFuture(packReload);

        writeMarker(root, experience, !pending.firstJoin(), !pending.firstJoin());
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();
        if (pending == null || !pending.firstJoin() || firstStartScheduled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        firstStartScheduled = true;

        DAI_ExperienceLaunchState.packReloadFuture().whenComplete((ignored, reloadError) ->
                server.execute(() -> {
                    if (reloadError != null) {
                        DAI_Core.LOGGER.warn(
                                "<DAI>: Experience datapack reload failed before first-start world bootstrap; continuing with safe world setup.",
                                reloadError
                        );
                    }
                    applyFirstStart(server, player, pending);
                })
        );
    }

    private static void applyFirstStart(
            MinecraftServer server,
            ServerPlayer player,
            DAI_ExperienceLaunchState.Pending pending
    ) {
        DAI_ExperienceDefinition experience = pending.definition();
        if (experience.worldgen().isBlank()) {
            DAI_ExperienceLaunchState.markWorldReady();
            return;
        }

        DAI_WorldgenRepository.reload();
        DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(experience.worldgen());
        if (worldgen == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' requested missing DAI worldgen definition '{}'.",
                    experience.id(), experience.worldgen()
            );
            DAI_ExperienceLaunchState.markWorldReady();
            return;
        }

        runCommand(server, "setworldspawn "
                + worldgen.spawn().x() + " " + worldgen.spawn().y() + " " + worldgen.spawn().z());

        for (String command : worldgen.generationCommands()) {
            runCommand(server, command);
        }

        for (DAI_WorldgenDefinition.StructurePlacement structure : worldgen.initialStructures()) {
            StringBuilder command = new StringBuilder("place template ")
                    .append(structure.structure()).append(' ')
                    .append(structure.x()).append(' ')
                    .append(structure.y()).append(' ')
                    .append(structure.z());
            if (!structure.rotation().isBlank() || !structure.mirror().isBlank()) {
                command.append(' ').append(structure.rotation().isBlank() ? "none" : structure.rotation());
                command.append(' ').append(structure.mirror().isBlank() ? "none" : structure.mirror());
            }
            runCommand(server, command.toString());
        }

        // The first engine build performed fill/place commands during
        // ServerStartedEvent, before the spawn chunks were actually loaded.
        // Running after PlayerLoggedInEvent guarantees the spawn-area chunks
        // exist; move the player onto the completed starter platform last.
        runCommand(
                server,
                "teleport " + player.getUUID() + " "
                        + worldgen.spawn().x() + " "
                        + worldgen.spawn().y() + " "
                        + worldgen.spawn().z() + " "
                        + worldgen.spawn().yaw() + " "
                        + worldgen.spawn().pitch()
        );

        DAI_ExperienceLaunchState.markWorldReady();

        DAI_Core.LOGGER.info(
                "<DAI>: Applied first-start DAI worldgen bootstrap '{}' for experience '{}'.",
                worldgen.id(), experience.id()
        );
    }


    /**
     * Copies/enables globally installed DAI ADDON packs for an ordinary world
     * that was not launched through a MAIN experience. Existing world pack
     * selections are preserved verbatim; this path never enforces MAIN
     * exclusivity and never removes/deselects user-owned datapacks.
     */
    private static CompletableFuture<?> installStandaloneAddonStack(
            MinecraftServer server,
            Path worldRoot
    ) {
        if (server == null || worldRoot == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!DAI_Config.autoEnableAddons()) {
            DAI_Core.LOGGER.info(
                    "<DAI>: Automatic ADDON datapack inclusion is disabled; standalone world addon layering skipped."
            );
            return CompletableFuture.completedFuture(null);
        }

        try {
            Path datapacks = worldRoot.resolve("datapacks");
            Files.createDirectories(datapacks);

            List<Path> installedAddons = new ArrayList<>();
            for (Path addon : DAI_DatapackMetadata.globalAddons()) {
                if (addon == null || !Files.exists(addon)) continue;

                Path target = installPackFile(datapacks, addon, "standalone addon");
                if (target != null && !installedAddons.contains(target)) {
                    installedAddons.add(target);
                }
            }

            if (installedAddons.isEmpty()) {
                DAI_Core.LOGGER.debug(
                        "<DAI>: No global DAI ADDON datapacks were available for standalone world '{}'.",
                        worldRoot.getFileName()
                );
                return CompletableFuture.completedFuture(null);
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared {} standalone DAI ADDON datapack(s) for world '{}'.",
                    installedAddons.size(),
                    worldRoot.getFileName()
            );

            return enableStandaloneAddons(server, installedAddons);
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not prepare standalone DAI ADDON datapacks for world '{}'.",
                    worldRoot.getFileName(),
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    /**
     * Enables only the supplied ADDON packs, starting from Minecraft's current
     * selected-pack set. This deliberately leaves every pre-existing selection
     * untouched, including any MAIN pack that was installed manually.
     */
    private static CompletableFuture<?> enableStandaloneAddons(
            MinecraftServer server,
            List<Path> addons
    ) {
        try {
            Object repository = invokeNoArg(server, "getPackRepository");
            if (repository == null) {
                throw new IllegalStateException("MinecraftServer#getPackRepository was unavailable");
            }

            invokeNoArg(repository, "reload");
            Set<String> available = stringSet(invokeNoArg(repository, "getAvailableIds"));
            Set<String> selected = stringSet(invokeNoArg(repository, "getSelectedIds"));
            LinkedHashSet<String> requested = new LinkedHashSet<>(selected);

            int enabledAddons = 0;
            for (Path addon : addons) {
                if (addon == null) continue;

                String packId = findPackIdByFilename(available, addon.getFileName().toString());
                if (packId == null) {
                    throw new IllegalStateException(
                            "Installed standalone addon was not exposed by PackRepository: "
                                    + addon.getFileName()
                    );
                }

                if (requested.add(packId)) enabledAddons++;
            }

            if (requested.equals(selected)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Standalone DAI ADDON stack already enabled ({} addon(s)); skipping redundant reload.",
                        addons.size()
                );
                return CompletableFuture.completedFuture(null);
            }

            Object result = invokeReloadResources(server, requested);
            if (result instanceof CompletableFuture<?> future) {
                final int addonTotal = enabledAddons;
                future.whenComplete((ignored, error) -> {
                    if (error == null) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Enabled {} standalone DAI ADDON datapack(s) without a MAIN experience.",
                                addonTotal
                        );
                    } else {
                        DAI_Core.LOGGER.error(
                                "<DAI>: Standalone DAI ADDON datapack reload failed.",
                                error
                        );
                    }
                });
                return future;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Enabled {} standalone DAI ADDON datapack(s) without a MAIN experience.",
                    enabledAddons
            );
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not enable standalone DAI ADDON datapacks.",
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static CompletableFuture<?> installExperienceStack(
            MinecraftServer server,
            Path worldRoot,
            Path sourceMainPack
    ) {
        if (server == null || worldRoot == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Path datapacks = worldRoot.resolve("datapacks");
            Files.createDirectories(datapacks);

            List<Path> installedTargets = new ArrayList<>();
            Path mainTarget = null;

            if (sourceMainPack != null && Files.exists(sourceMainPack)) {
                mainTarget = installPackFile(datapacks, sourceMainPack, "main experience");
                if (mainTarget != null) installedTargets.add(mainTarget);
            }

            int addonCount = 0;
            if (DAI_Config.autoEnableAddons()) {
                for (Path addon : DAI_DatapackMetadata.globalAddons()) {
                    if (addon == null || !Files.exists(addon)) continue;

                    Path normalizedAddon = addon.toAbsolutePath().normalize();
                    Path normalizedMain = sourceMainPack == null
                            ? null
                            : sourceMainPack.toAbsolutePath().normalize();
                    if (normalizedMain != null && normalizedMain.equals(normalizedAddon)) continue;

                    Path target = installPackFile(datapacks, addon, "addon");
                    if (target != null) {
                        if (!installedTargets.contains(target)) installedTargets.add(target);
                        addonCount++;
                    }
                }
            } else {
                DAI_Core.LOGGER.info(
                        "<DAI>: Automatic ADDON datapack inclusion is disabled by configuration; preparing the main experience only."
                );
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared datapack stack for experience: main={} addon(s)={} target='{}'.",
                    mainTarget == null ? "<save-owned/config>" : mainTarget.getFileName(),
                    addonCount,
                    datapacks
            );

            return enablePackStack(server, worldRoot, mainTarget, installedTargets);
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not prepare the DAI experience/addon datapack stack.",
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    /**
     * Copies or updates one source pack without reloading resources. Reload is
     * intentionally batched after every addon is prepared.
     */
    private static Path installPackFile(
            Path datapacks,
            Path sourcePack,
            String roleLabel
    ) throws Exception {
        if (datapacks == null || sourcePack == null || !Files.exists(sourcePack)) return null;

        Path source = sourcePack.toAbsolutePath().normalize();
        Path target = datapacks.resolve(sourcePack.getFileName().toString()).toAbsolutePath().normalize();
        if (!target.startsWith(datapacks.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("Invalid datapack target: " + target);
        }

        if (source.equals(target)) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: {} datapack already belongs to this save: '{}'.",
                    roleLabel,
                    target.getFileName()
            );
            return target;
        }

        // Windows can keep ZIP datapacks open through PackRepository. Do not
        // replace an identical archive and trigger a needless locked-file error.
        if (Files.exists(target) && samePackContent(source, target)) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: {} datapack '{}' is already current in the save.",
                    roleLabel,
                    target.getFileName()
            );
            return target;
        }

        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }

        DAI_Core.LOGGER.info(
                "<DAI>: Installed {} datapack '{}' into world save.",
                roleLabel,
                source.getFileName()
        );
        return target;
    }

    private static boolean samePackContent(Path source, Path target) {
        if (source == null || target == null) return false;
        try {
            if (!Files.exists(source) || !Files.exists(target)) return false;
            if (Files.isDirectory(source) || Files.isDirectory(target)) return false;
            if (Files.size(source) != Files.size(target)) return false;
            return Files.mismatch(source, target) == -1L;
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not compare datapack source '{}' with installed target '{}'.",
                    source,
                    target,
                    exception
            );
            return false;
        }
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        if (Files.exists(target)) {
            try (Stream<Path> old = Files.walk(target)) {
                for (Path path : old.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                    if (!path.equals(target)) Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(target);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path path : files.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Enables the selected MAIN plus every prepared ADDON in one reload. Any
     * other DAI MAIN pack already installed in this save is removed from the
     * selected set, enforcing one active experience without deleting files.
     */
    private static CompletableFuture<?> enablePackStack(
            MinecraftServer server,
            Path worldRoot,
            Path selectedMain,
            List<Path> prepared
    ) {
        try {
            Object repository = invokeNoArg(server, "getPackRepository");
            if (repository == null) {
                throw new IllegalStateException("MinecraftServer#getPackRepository was unavailable");
            }

            invokeNoArg(repository, "reload");
            Set<String> available = stringSet(invokeNoArg(repository, "getAvailableIds"));
            Set<String> selected = stringSet(invokeNoArg(repository, "getSelectedIds"));
            LinkedHashSet<String> requested = new LinkedHashSet<>(selected);

            Path selectedMainNormalized = selectedMain == null
                    ? null
                    : selectedMain.toAbsolutePath().normalize();

            // Enforce at most one DAI MAIN pack. Ordinary/non-DAI datapacks
            // and every ADDON remain untouched by this exclusivity rule. A
            // config-authored experience has no source MAIN pack, so in that
            // case every installed DAI MAIN is deselected.
            Path worldDatapacks = worldRoot.resolve("datapacks");
            if (Files.isDirectory(worldDatapacks)) {
                try (Stream<Path> entries = Files.list(worldDatapacks)) {
                    for (Path pack : entries.sorted().toList()) {
                        if (DAI_DatapackMetadata.role(pack) != DAI_DatapackRole.MAIN) continue;
                        Path normalized = pack.toAbsolutePath().normalize();
                        if (selectedMainNormalized != null && normalized.equals(selectedMainNormalized)) continue;

                        String id = findPackIdByFilename(available, pack.getFileName().toString());
                        if (id != null && requested.remove(id)) {
                            DAI_Core.LOGGER.info(
                                    "<DAI>: Disabled alternate MAIN datapack '{}' while launching '{}'.",
                                    id,
                                    selectedMain == null ? "<config experience>" : selectedMain.getFileName()
                            );
                        }
                    }
                }
            }

            int enabledAddons = 0;
            for (Path pack : prepared) {
                if (pack == null) continue;
                String packId = findPackIdByFilename(available, pack.getFileName().toString());
                if (packId == null) {
                    throw new IllegalStateException(
                            "Installed datapack was not exposed by PackRepository: " + pack.getFileName()
                    );
                }
                requested.add(packId);
                if (DAI_DatapackMetadata.role(pack) == DAI_DatapackRole.ADDON) enabledAddons++;
            }

            if (requested.equals(selected)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: DAI datapack stack already enabled (1 main max, {} addon(s)); skipping redundant reload.",
                        enabledAddons
                );
                return CompletableFuture.completedFuture(null);
            }

            Object result = invokeReloadResources(server, requested);
            if (result instanceof CompletableFuture<?> future) {
                final int addonTotal = enabledAddons;
                future.whenComplete((ignored, error) -> {
                    if (error == null) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Enabled DAI datapack stack with one MAIN experience and {} ADDON pack(s).",
                                addonTotal
                        );
                    } else {
                        DAI_Core.LOGGER.error(
                                "<DAI>: DAI experience/addon datapack stack failed to reload.",
                                error
                        );
                    }
                });
                return future;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Enabled DAI datapack stack with one MAIN experience and {} ADDON pack(s).",
                    enabledAddons
            );
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not enable the DAI experience/addon datapack stack.",
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static String findPackIdByFilename(Set<String> available, String fileName) {
        String needle = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return null;

        for (String id : available) {
            String normalized = id.toLowerCase(Locale.ROOT);
            if (normalized.equals("file/" + needle)
                    || normalized.endsWith("/" + needle)
                    || normalized.endsWith(needle)) {
                return id;
            }
        }
        return null;
    }

    private static String findInstalledPackId(
            Set<String> before,
            Set<String> available,
            String fileName
    ) {
        String needle = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);

        for (String id : available) {
            String normalized = id.toLowerCase(Locale.ROOT);
            if (normalized.equals("file/" + needle)
                    || normalized.endsWith("/" + needle)
                    || normalized.endsWith(needle)) {
                return id;
            }
        }

        if (before != null && !before.isEmpty()) {
            for (String id : available) {
                if (!before.contains(id)) return id;
            }
        }
        return null;
    }

    private static Object invokeReloadResources(MinecraftServer server, Collection<String> selected) throws Exception {
        for (Method method : server.getClass().getMethods()) {
            if (!method.getName().equals("reloadResources")) continue;
            if (method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (!Collection.class.isAssignableFrom(type)) continue;
            return method.invoke(server, new ArrayList<>(selected));
        }
        throw new NoSuchMethodException("MinecraftServer#reloadResources(Collection)");
    }

    private static Set<String> stringSet(Object value) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry != null) result.add(String.valueOf(entry));
            }
        }
        return result;
    }

    private static Object invokeNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (Throwable ignored) { }

        try {
            Method method = target.getClass().getDeclaredMethod(name);
            if (!method.canAccess(target) && !method.trySetAccessible()) return null;
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Marks first-join startup only after the client has successfully resolved
     * and dispatched its startup action. Keeping this separate from worldgen
     * lets interrupted/restart-gated first launches repair themselves.
     */
    public static void markFirstJoinDispatched(String experienceId) {
        Path root = currentWorldRoot;
        DAI_ExperienceDefinition experience = currentExperience;
        if (root == null || experience == null) return;
        if (experienceId != null && !experienceId.isBlank() && !experience.id().equals(experienceId.trim())) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignored first-join marker for '{}' while '{}' is active.",
                    experienceId, experience.id()
            );
            return;
        }
        writeMarker(root, experience, true, true);
        DAI_Core.LOGGER.info(
                "<DAI>: Marked experience '{}' first-join startup as dispatched.",
                experience.id()
        );
    }

    private static void writeMarker(
            Path root,
            DAI_ExperienceDefinition experience,
            boolean firstJoinComplete,
            boolean startupDispatched
    ) {
        try {
            Path target = root.resolve("dai").resolve("experience.json");
            Files.createDirectories(target.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("experience", experience.id());
            json.addProperty("save_id", experience.saveId());
            json.addProperty("worldgen", experience.worldgen());
            json.addProperty("first_join_complete", firstJoinComplete);
            json.addProperty("startup_dispatched", startupDispatched);
            // Bump whenever first-join completion semantics change. This lets
            // interrupted worlds created by an older handoff implementation
            // repair themselves once instead of being permanently treated as
            // initialized.
            json.addProperty("handoff_version", 2);
            Files.writeString(
                    target,
                    new GsonBuilder().setPrettyPrinting().create().toJson(json),
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not write experience marker for '{}'.", experience.id(), exception);
        }
    }

    /** Reflection keeps this isolated from command-manager mapping changes. */
    private static void runCommand(MinecraftServer server, String raw) {
        if (server == null || raw == null || raw.isBlank()) return;
        String command = raw.trim();
        if (command.startsWith("/")) command = command.substring(1);

        try {
            Object commands = server.getCommands();
            Object source = server.createCommandSourceStack();

            for (Method method : commands.getClass().getMethods()) {
                String name = method.getName();
                if (!name.equals("performPrefixedCommand") && !name.equals("performCommand")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 2 || types[1] != String.class) continue;
                if (!types[0].isInstance(source)) continue;
                method.invoke(commands, source, command);
                return;
            }

            DAI_Core.LOGGER.warn("<DAI>: No compatible server command executor found for worldgen command '{}'.", command);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.warn("<DAI>: DAI worldgen command failed: '{}'.", command, exception);
        }
    }
}
