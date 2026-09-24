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
import io.github.j12h36h.dai.packs.DAI_DatapackSync;
import io.github.j12h36h.dai.packs.DAI_WorldAddonSelection;
import io.github.j12h36h.dai.runtime.DAI_ShellSessionState;
import io.github.j12h36h.dai.runtime.DAI_StandaloneLaunchState;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/** Applies high-level DAI worldgen bootstrap data when an experience world starts. */
public final class DAI_WorldgenRuntime {

    private static volatile boolean firstStartScheduled;
    private static volatile Path currentWorldRoot;
    private static volatile DAI_ExperienceDefinition currentExperience;
    private static volatile DAI_ExperienceLaunchState.Pending currentPendingLaunch;
    private static volatile boolean packBootstrapPending;
    private static volatile UUID firstStartPlayer;
    private static volatile CompletableFuture<?> currentPackBootstrap = CompletableFuture.completedFuture(null);

    private DAI_WorldgenRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DAI_WorldgenRuntime::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DAI_WorldgenRuntime::onPlayerLoggedIn);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path root = server.getWorldPath(LevelResource.ROOT);
        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();

        // The reserved shell is DAI Engine infrastructure. Never mutate its
        // datapack stack, and never leave a deferred bootstrap armed for it.
        if (pending == null && DAI_ShellSessionState.consumeForWorld(root)) {
            currentWorldRoot = null;
            currentExperience = null;
            currentPendingLaunch = null;
            firstStartScheduled = false;
            packBootstrapPending = false;
            firstStartPlayer = null;
            currentPackBootstrap = CompletableFuture.completedFuture(null);
            DAI_Core.LOGGER.info(
                    "<DAI>: Reserved shell world detected; skipping standalone ADDON synchronization and experience bootstrap."
            );
            return;
        }

        currentWorldRoot = root;
        currentPendingLaunch = pending;
        currentExperience = pending == null ? null : pending.definition();
        firstStartScheduled = false;
        packBootstrapPending = true;
        firstStartPlayer = null;
        currentPackBootstrap = CompletableFuture.completedFuture(null);

        /*
         * IMPORTANT: do not reload datapacks from ServerStartedEvent. On an
         * integrated server the local client can already be inside its
         * configuration/login protocol while this event fires. Reloading the
         * selected pack set here can invalidate the registry snapshot being
         * sent to the client and surface as "Network Protocol Error" around
         * the late world-loading percentages. The exact same pack bootstrap is
         * now performed from PlayerLoggedInEvent, after the connection is fully
         * established, where Minecraft's normal live reload path is safe.
         */
        if (pending != null) {
            writeMarker(root, pending.definition(), !pending.firstJoin(), !pending.firstJoin());
        } else {
            writeRuntimeMarker(root, "vanilla", "");
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();
        if (pending == null) pending = currentPendingLaunch;
        DAI_ExperienceLaunchState.Pending launch = pending;

        /*
         * Queue the reload after PlayerLoggedInEvent returns. This lets the
         * integrated client's initial login/configuration packet sequence
         * finish before any PackRepository change begins. A gate is published
         * immediately so client-side experience activation cannot outrun it.
         */
        CompletableFuture<Void> loginSafeGate = new CompletableFuture<>();
        if (launch != null) DAI_ExperienceLaunchState.setPackReloadFuture(loginSafeGate);

        boolean runFirstStart = launch != null && launch.firstJoin() && !firstStartScheduled;
        if (runFirstStart) {
            firstStartScheduled = true;
            firstStartPlayer = player.getUUID();
        }

        server.execute(() -> {
            CompletableFuture<?> packReload = beginDeferredPackBootstrap(server, launch);
            packReload.whenComplete((ignored, reloadError) -> server.execute(() -> {
                if (reloadError == null) loginSafeGate.complete(null);
                else loginSafeGate.completeExceptionally(reloadError);

                if (runFirstStart) {
                    if (reloadError != null) {
                        DAI_Core.LOGGER.warn(
                                "<DAI>: Experience datapack reload failed before first-start world bootstrap; continuing with safe world setup.",
                                reloadError
                        );
                    }
                    applyFirstStart(server, player, launch);
                }
            }));
        });
    }

    private static synchronized CompletableFuture<?> beginDeferredPackBootstrap(
            MinecraftServer server,
            DAI_ExperienceLaunchState.Pending pending
    ) {
        if (!packBootstrapPending) return currentPackBootstrap;
        packBootstrapPending = false;

        Path root = currentWorldRoot != null
                ? currentWorldRoot
                : server.getWorldPath(LevelResource.ROOT);
        try {
            currentPackBootstrap = pending == null
                    ? installStandaloneAddonStack(server, root)
                    : installExperienceStack(server, root, pending);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error("<DAI>: Deferred world datapack bootstrap failed.", exception);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            currentPackBootstrap = failed;
        }
        return currentPackBootstrap;
    }

    private static void applyFirstStart(
            MinecraftServer server,
            ServerPlayer player,
            DAI_ExperienceLaunchState.Pending pending
    ) {
        DAI_ExperienceDefinition experience = pending.definition();
        String selectedWorldgen = pending.worldgenOverride().isBlank()
                ? experience.worldgen()
                : pending.worldgenOverride();
        if (selectedWorldgen.isBlank()) {
            DAI_ExperienceLaunchState.markWorldReady();
            return;
        }

        DAI_WorldgenRepository.reload();
        DAI_WorldgenDefinition worldgen = DAI_WorldgenRepository.get(selectedWorldgen);
        if (worldgen == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Experience '{}' requested missing DAI worldgen definition '{}'.",
                    experience.id(), selectedWorldgen
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
     * that was not launched through a MAIN experience. Existing selections
     * are preserved. The only files this path may replace/remove are stale
     * DAI-managed mirrors whose stable identity matches a newer global pack;
     * unrelated user-owned datapacks remain untouched.
     */
    private static CompletableFuture<?> installStandaloneAddonStack(
            MinecraftServer server,
            Path worldRoot
    ) {
        if (server == null || worldRoot == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Path datapacks = worldRoot.resolve("datapacks");
            Files.createDirectories(datapacks);

            Set<String> selectedBefore = selectedWorldPackFilenames(server, datapacks);
            DAI_DatapackSync.SyncResult sync = DAI_DatapackSync.reconcileExistingWorldPacks(datapacks);
            List<Path> preservedSelections = selectedReplacementTargets(sync, selectedBefore, null);
            Set<String> staleNames = replacedOldFileNames(sync);
            boolean forceReload = intersects(selectedBefore, sync.changedFileNames());

            DAI_StandaloneLaunchState.Selection explicitSelection = DAI_StandaloneLaunchState.consume();
            Optional<DAI_WorldAddonSelection.Selection> persisted = DAI_WorldAddonSelection.read(worldRoot);

            // A one-shot creation selection becomes the world's persistent,
            // version-independent selection as soon as the new server joins.
            if (explicitSelection != null) {
                LinkedHashSet<String> stableIds = new LinkedHashSet<>();
                for (Path addon : DAI_DatapackMetadata.globalAddons()) {
                    if (addon == null || addon.getFileName() == null) continue;
                    if (!explicitSelection.includes(addon.getFileName().toString())) continue;
                    String stableId = DAI_WorldAddonSelection.normalize(DAI_DatapackMetadata.stableId(addon));
                    if (!stableId.isBlank()) stableIds.add(stableId);
                }
                DAI_WorldAddonSelection.write(worldRoot, stableIds);
                persisted = Optional.of(new DAI_WorldAddonSelection.Selection(Set.copyOf(stableIds)));
            }

            DAI_WorldAddonSelection.Selection exactSelection = persisted.orElse(null);
            boolean useConfiguredAutomaticSet = exactSelection == null && DAI_Config.autoEnableAddons();
            List<Path> installedAddons = new ArrayList<>();

            if (exactSelection != null || useConfiguredAutomaticSet) {
                for (Path addon : DAI_DatapackMetadata.globalAddons()) {
                    if (addon == null || !Files.exists(addon)) continue;
                    if (exactSelection != null && !exactSelection.includes(addon)) continue;
                    Path target = installPackFile(datapacks, addon, "standalone addon");
                    if (target != null && !installedAddons.contains(target)) installedAddons.add(target);
                }
            }

            return enableStandaloneAddons(
                    server,
                    worldRoot,
                    installedAddons,
                    preservedSelections,
                    staleNames,
                    forceReload,
                    exactSelection
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not prepare standalone DAI datapacks for world '{}'.",
                    worldRoot.getFileName(),
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static CompletableFuture<?> enableStandaloneAddons(
            MinecraftServer server,
            Path worldRoot,
            List<Path> addons,
            List<Path> preservedSelections,
            Set<String> staleFileNames,
            boolean forceReload,
            DAI_WorldAddonSelection.Selection exactSelection
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

            if (staleFileNames != null && !staleFileNames.isEmpty()) {
                requested.removeIf(id -> matchesAnyFilename(id, staleFileNames));
            }

            // An explicit per-world marker is authoritative. Deselect DAI
            // ADDONs not present in it, but never touch ordinary datapacks.
            if (exactSelection != null) {
                Path worldDatapacks = worldRoot.resolve("datapacks");
                if (Files.isDirectory(worldDatapacks)) {
                    try (Stream<Path> entries = Files.list(worldDatapacks)) {
                        for (Path pack : entries.sorted().toList()) {
                            if (DAI_DatapackMetadata.role(pack) != DAI_DatapackRole.ADDON) continue;
                            if (exactSelection.includes(pack)) continue;
                            String id = findPackIdByFilename(available, pack.getFileName().toString());
                            if (id != null && requested.remove(id)) {
                                DAI_Core.LOGGER.info(
                                        "<DAI>: Disabled world ADDON '{}' because it is not selected for this save.",
                                        id
                                );
                            }
                        }
                    }
                }
            }

            for (Path replacement : preservedSelections) {
                if (replacement == null) continue;
                if (exactSelection != null && DAI_DatapackMetadata.role(replacement) == DAI_DatapackRole.ADDON
                        && !exactSelection.includes(replacement)) continue;
                String packId = findPackIdByFilename(available, replacement.getFileName().toString());
                if (packId == null) continue;
                requested.add(packId);
            }

            int enabledAddons = 0;
            for (Path addon : addons) {
                if (addon == null) continue;
                String packId = findPackIdByFilename(available, addon.getFileName().toString());
                if (packId == null) {
                    throw new IllegalStateException(
                            "Installed standalone addon was not exposed by PackRepository: " + addon.getFileName()
                    );
                }
                if (requested.add(packId)) enabledAddons++;
            }

            if (!forceReload && requested.equals(selected)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Standalone DAI datapack stack already current; skipping redundant reload."
                );
                return CompletableFuture.completedFuture(null);
            }

            Object result = invokeReloadResources(server, requested);
            if (result instanceof CompletableFuture<?> future) {
                final int addonTotal = enabledAddons;
                future.whenComplete((ignored, error) -> {
                    if (error == null) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Reloaded standalone DAI datapacks after player join ({} newly enabled addon(s)).",
                                addonTotal
                        );
                    } else {
                        DAI_Core.LOGGER.error("<DAI>: Standalone DAI datapack reload failed.", error);
                    }
                });
                return future;
            }

            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error("<DAI>: Could not enable standalone DAI datapacks.", exception);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    private static CompletableFuture<?> installExperienceStack(
            MinecraftServer server,
            Path worldRoot,
            DAI_ExperienceLaunchState.Pending pending
    ) {
        if (server == null || worldRoot == null) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Path sourceMainPack = pending == null ? null : pending.sourcePack();
            Path datapacks = worldRoot.resolve("datapacks");
            Files.createDirectories(datapacks);

            Set<String> selectedBefore = selectedWorldPackFilenames(server, datapacks);
            DAI_DatapackSync.SyncResult sync =
                    DAI_DatapackSync.reconcileExistingWorldPacks(datapacks);
            boolean forceReload = intersects(selectedBefore, sync.changedFileNames());

            List<Path> installedTargets = new ArrayList<>();
            // Preserve selected ADDONs whose versioned filename was replaced.
            // MAIN ownership is handled separately by the one-main rule below.
            DAI_ExperienceDefinition.AddonPolicy addonPolicy = currentExperience == null
                    ? DAI_ExperienceDefinition.AddonPolicy.DEFAULT
                    : currentExperience.addons();
            DAI_WorldAddonSelection.Selection exactSelection =
                    DAI_WorldAddonSelection.read(worldRoot).orElse(null);

            // A DAI 4.3 Loaded-screen selection is authoritative for a new
            // Experience save. Persist stable ADDON ids before pack enabling
            // so version changes do not alter the user's world configuration.
            if (pending != null && pending.explicitAddonSelection()) {
                LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
                for (String stableId : pending.selectedAddonIds()) {
                    String normalized = DAI_WorldAddonSelection.normalize(stableId);
                    if (normalized.isBlank()) continue;
                    if (!addonPolicy.allows(normalized)) continue;
                    selectedIds.add(normalized);
                }
                DAI_WorldAddonSelection.write(worldRoot, selectedIds);
                exactSelection = new DAI_WorldAddonSelection.Selection(Set.copyOf(selectedIds));
            }
            for (Path replacement : selectedReplacementTargets(
                    sync,
                    selectedBefore,
                    DAI_DatapackRole.ADDON
            )) {
                if (addonPolicyAllows(addonPolicy, replacement)
                        && (exactSelection == null || exactSelection.includes(replacement))) {
                    installedTargets.add(replacement);
                }
            }

            Path mainTarget = null;

            if (sourceMainPack != null && Files.exists(sourceMainPack)) {
                if (isEmbeddedModPack(sourceMainPack)) {
                    DAI_Core.LOGGER.info(
                            "<DAI>: MAIN experience '{}' is embedded in mod archive '{}'; no world-local datapack copy is required.",
                            currentExperience == null ? "<unknown>" : currentExperience.id(),
                            sourceMainPack.getFileName()
                    );
                } else {
                    mainTarget = installPackFile(datapacks, sourceMainPack, "main experience");
                    if (mainTarget != null && !installedTargets.contains(mainTarget)) {
                        installedTargets.add(mainTarget);
                    }
                }
            }

            int addonCount = 0;
            if ((exactSelection != null || DAI_Config.autoEnableAddons()) && addonPolicy.enabled()) {
                for (Path addon : DAI_DatapackMetadata.globalAddons()) {
                    if (addon == null || !Files.exists(addon)) continue;
                    if (exactSelection != null && !exactSelection.includes(addon)) continue;

                    Path normalizedAddon = addon.toAbsolutePath().normalize();
                    Path normalizedMain = sourceMainPack == null
                            ? null
                            : sourceMainPack.toAbsolutePath().normalize();
                    if (normalizedMain != null && normalizedMain.equals(normalizedAddon)) continue;
                    if (!addonPolicyAllows(addonPolicy, addon)) {
                        DAI_Core.LOGGER.debug(
                                "<DAI>: Experience '{}' rejected ADDON '{}' (stable id='{}').",
                                currentExperience == null ? "<unknown>" : currentExperience.id(),
                                addon.getFileName(),
                                DAI_DatapackMetadata.stableId(addon)
                        );
                        continue;
                    }

                    Path target = installPackFile(datapacks, addon, "addon");
                    if (target != null) {
                        if (!installedTargets.contains(target)) installedTargets.add(target);
                        addonCount++;
                    }
                }
            } else if (!addonPolicy.enabled()) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience '{}' disables DAI ADDON layering.",
                        currentExperience == null ? "<unknown>" : currentExperience.id()
                );
            } else {
                DAI_Core.LOGGER.info(
                        "<DAI>: Automatic ADDON datapack inclusion is disabled by player configuration; preparing the main experience only."
                );
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared datapack stack for experience: main={} addon(s)={} target='{}'.",
                    mainTarget == null ? "<save-owned/config>" : mainTarget.getFileName(),
                    addonCount,
                    datapacks
            );

            return enablePackStack(
                    server,
                    worldRoot,
                    mainTarget,
                    installedTargets,
                    forceReload,
                    addonPolicy,
                    exactSelection
            );
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
            List<Path> prepared,
            boolean forceReload,
            DAI_ExperienceDefinition.AddonPolicy addonPolicy,
            DAI_WorldAddonSelection.Selection exactSelection
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
            // remain untouched. DAI ADDONs are additionally filtered through
            // the active experience policy; a disabled or non-whitelisted addon
            // is deselected without deleting its files. A config-authored
            // experience has no source MAIN pack, so every installed DAI MAIN
            // is deselected in that case.
            Path worldDatapacks = worldRoot.resolve("datapacks");
            if (Files.isDirectory(worldDatapacks)) {
                try (Stream<Path> entries = Files.list(worldDatapacks)) {
                    for (Path pack : entries.sorted().toList()) {
                        DAI_DatapackRole role = DAI_DatapackMetadata.role(pack);
                        if (role == DAI_DatapackRole.MAIN) {
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
                            continue;
                        }

                        if (role == DAI_DatapackRole.ADDON
                                && (!addonPolicyAllows(addonPolicy, pack)
                                || (exactSelection != null && !exactSelection.includes(pack)))) {
                            String id = findPackIdByFilename(available, pack.getFileName().toString());
                            if (id != null && requested.remove(id)) {
                                DAI_Core.LOGGER.info(
                                        "<DAI>: Disabled ADDON '{}' for experience '{}' (stable id='{}').",
                                        id,
                                        currentExperience == null ? "<unknown>" : currentExperience.id(),
                                        DAI_DatapackMetadata.stableId(pack)
                                );
                            }
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

            if (!forceReload && requested.equals(selected)) {
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

    private static boolean addonPolicyAllows(
            DAI_ExperienceDefinition.AddonPolicy policy,
            Path addon
    ) {
        DAI_ExperienceDefinition.AddonPolicy effective = policy == null
                ? DAI_ExperienceDefinition.AddonPolicy.DEFAULT
                : policy;
        if (!effective.enabled()) return false;
        if (effective.whitelist().isEmpty()) return true;
        return effective.allows(DAI_DatapackMetadata.stableId(addon));
    }

    private static Set<String> selectedWorldPackFilenames(
            MinecraftServer server,
            Path datapacks
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (server == null || datapacks == null || !Files.isDirectory(datapacks)) return result;

        try {
            Object repository = invokeNoArg(server, "getPackRepository");
            if (repository == null) return result;

            Set<String> available = stringSet(invokeNoArg(repository, "getAvailableIds"));
            Set<String> selected = stringSet(invokeNoArg(repository, "getSelectedIds"));

            try (Stream<Path> entries = Files.list(datapacks)) {
                for (Path pack : entries.sorted().toList()) {
                    String fileName = pack.getFileName().toString();
                    String id = findPackIdByFilename(available, fileName);
                    if (id != null && selected.contains(id)) result.add(fileName);
                }
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not capture selected world datapack filenames before synchronization.",
                    exception
            );
        }
        return result;
    }

    private static List<Path> selectedReplacementTargets(
            DAI_DatapackSync.SyncResult sync,
            Set<String> selectedBefore,
            DAI_DatapackRole roleFilter
    ) {
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        if (sync == null || selectedBefore == null || selectedBefore.isEmpty()) {
            return List.of();
        }

        for (DAI_DatapackSync.Replacement replacement : sync.replacements()) {
            if (replacement == null || replacement.newPath() == null) continue;
            if (roleFilter != null && replacement.role() != roleFilter) continue;
            if (!selectedBefore.contains(replacement.oldFileName())) continue;
            result.add(replacement.newPath().toAbsolutePath().normalize());
        }
        return List.copyOf(result);
    }

    private static Set<String> replacedOldFileNames(DAI_DatapackSync.SyncResult sync) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (sync == null) return result;
        for (DAI_DatapackSync.Replacement replacement : sync.replacements()) {
            if (replacement == null || replacement.oldFileName() == null) continue;
            if (!replacement.oldFileName().isBlank()) result.add(replacement.oldFileName());
        }
        return result;
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return false;
        for (String value : left) {
            if (right.contains(value)) return true;
        }
        return false;
    }

    private static boolean isEmbeddedModPack(Path source) {
        if (source == null || !Files.isRegularFile(source)) return false;
        return source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static boolean matchesAnyFilename(String packId, Set<String> fileNames) {
        if (packId == null || fileNames == null || fileNames.isEmpty()) return false;
        for (String fileName : fileNames) {
            if (packIdMatchesFilename(packId, fileName)) return true;
        }
        return false;
    }

    private static boolean packIdMatchesFilename(String packId, String fileName) {
        String normalized = packId == null ? "" : packId.toLowerCase(Locale.ROOT);
        String needle = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || needle.isBlank()) return false;
        return normalized.equals("file/" + needle)
                || normalized.endsWith("/" + needle)
                || normalized.endsWith(needle);
    }

    private static String findPackIdByFilename(Set<String> available, String fileName) {
        String needle = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (needle.isBlank()) return null;

        for (String id : available) {
            if (packIdMatchesFilename(id, needle)) return id;
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
    public static boolean markFirstJoinDispatched(ServerPlayer sender, String experienceId) {
        Path root = currentWorldRoot;
        DAI_ExperienceDefinition experience = currentExperience;
        DAI_ExperienceLaunchState.Pending launch = currentPendingLaunch;
        if (sender == null || root == null || experience == null || launch == null || !launch.firstJoin()) return false;

        /*
         * This acknowledgement is intentionally NOT a generic file-write
         * capability. Only the exact player selected by the server as the
         * first-start bootstrap actor may acknowledge the active experience,
         * and only after server-side world bootstrap has completed. The file
         * path and contents remain entirely server-derived.
         */
        if (firstStartPlayer == null || !firstStartPlayer.equals(sender.getUUID())) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected first-join marker acknowledgement from non-bootstrap player '{}'.",
                    sender.getUUID()
            );
            return false;
        }
        if (!DAI_ExperienceLaunchState.worldReady()) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Rejected early first-join marker acknowledgement from '{}'; world bootstrap is not complete.",
                    sender.getUUID()
            );
            return false;
        }
        if (experienceId != null && !experienceId.isBlank() && !experience.id().equals(experienceId.trim())) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Ignored first-join marker for '{}' while '{}' is active.",
                    experienceId, experience.id()
            );
            return false;
        }

        writeMarker(root, experience, true, true);
        firstStartPlayer = null;
        DAI_Core.LOGGER.info(
                "<DAI>: Marked experience '{}' first-join startup as dispatched by authorized bootstrap player '{}'.",
                experience.id(), sender.getUUID()
        );
        return true;
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
            json.addProperty("runtime", "experience");
            json.addProperty("engine_feature_level", DAI_Core.FEATURE_LEVEL);
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
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(
                    temporary,
                    new GsonBuilder().setPrettyPrinting().create().toJson(json),
                    StandardCharsets.UTF_8
            );
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            writeRuntimeMarker(root, "experience", experience.id());
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not write experience marker for '{}'.", experience.id(), exception);
        }
    }

    /**
     * Small engine-owned save identity. Gameplay systems can distinguish a
     * normal Minecraft+ADDON world from an Experience without inspecting the
     * installed global pack library. Experience-specific state remains in
     * experience.json.
     */
    private static void writeRuntimeMarker(Path root, String runtime, String experienceId) {
        if (root == null) return;
        try {
            Path target = root.resolve("dai").resolve("runtime.json");
            Files.createDirectories(target.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("runtime", runtime == null || runtime.isBlank() ? "vanilla" : runtime);
            json.addProperty("engine_feature_level", DAI_Core.FEATURE_LEVEL);
            if (experienceId != null && !experienceId.isBlank()) {
                json.addProperty("experience", experienceId);
            }
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not write world runtime marker for '{}'.", root.getFileName(), exception);
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
