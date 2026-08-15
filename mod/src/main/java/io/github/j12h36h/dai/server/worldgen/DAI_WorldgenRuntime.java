package io.github.j12h36h.dai.server.worldgen;

import io.github.j12h36h.dai.worldgen.DAI_WorldgenDefinition;
import io.github.j12h36h.dai.worldgen.DAI_WorldgenRepository;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceLaunchState;
import io.github.j12h36h.dai.logics.core.DAI_Core;
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
        DAI_ExperienceLaunchState.Pending pending = DAI_ExperienceLaunchState.pending();
        if (pending == null) return;

        MinecraftServer server = event.getServer();
        DAI_ExperienceDefinition experience = pending.definition();
        Path root = server.getWorldPath(LevelResource.ROOT);
        currentWorldRoot = root;
        currentExperience = experience;
        firstStartScheduled = false;

        // A newly created vanilla save does not automatically inherit the
        // datapack that declared the title-screen experience. Install that
        // exact source pack into the new save, then ask the integrated server
        // to reload it before client-side startup actions are resolved.
        CompletableFuture<?> packReload = installExperiencePack(server, root, pending.sourcePack());
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

    private static CompletableFuture<?> installExperiencePack(
            MinecraftServer server,
            Path worldRoot,
            Path sourcePack
    ) {
        if (server == null || worldRoot == null || sourcePack == null || !Files.exists(sourcePack)) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            Path datapacks = worldRoot.resolve("datapacks");
            Files.createDirectories(datapacks);
            Path target = datapacks.resolve(sourcePack.getFileName().toString()).toAbsolutePath().normalize();
            Path source = sourcePack.toAbsolutePath().normalize();

            if (source.equals(target)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience datapack already belongs to this save; ensuring it is enabled: '{}'.",
                        target
                );
                return ensureInstalledPackEnabled(server, target);
            }

            // Windows keeps ZIP datapacks open while PackRepository is using them.
            // Replacing an already-loaded target with REPLACE_EXISTING therefore
            // throws FileSystemException even when the global source and the save
            // copy are byte-for-byte identical. Compare before copying and reuse
            // the existing save-local file when nothing actually changed.
            if (Files.exists(target) && samePackContent(source, target)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience datapack '{}' is already installed with identical content; skipping locked-file replacement.",
                        target.getFileName()
                );
                return ensureInstalledPackEnabled(server, target);
            }

            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Installed experience datapack '{}' into '{}'.",
                    source,
                    datapacks
            );

            return reloadInstalledPack(server, target);
        } catch (Exception exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not install the experience datapack into the launched save.",
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
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
                    "<DAI>: Could not compare experience datapack source '{}' with installed target '{}'.",
                    source,
                    target,
                    exception
            );
            return false;
        }
    }

    /**
     * Avoids a full server resource reload when an already-installed experience
     * pack is already selected. Besides being faster, this avoids reopening and
     * replacing ZIP files that Windows currently has locked through PackRepository.
     */
    private static CompletableFuture<?> ensureInstalledPackEnabled(
            MinecraftServer server,
            Path installedPack
    ) {
        try {
            Object repository = invokeNoArg(server, "getPackRepository");
            if (repository == null) {
                return reloadInstalledPack(server, installedPack);
            }

            invokeNoArg(repository, "reload");
            Set<String> available = stringSet(invokeNoArg(repository, "getAvailableIds"));
            Set<String> selected = stringSet(invokeNoArg(repository, "getSelectedIds"));
            String packId = findInstalledPackId(Set.of(), available, installedPack.getFileName().toString());

            if (packId != null && selected.contains(packId)) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Experience datapack '{}' is already enabled; skipping redundant server resource reload.",
                        packId
                );
                return CompletableFuture.completedFuture(null);
            }
        } catch (Throwable exception) {
            DAI_Core.LOGGER.debug(
                    "<DAI>: Could not determine whether experience datapack '{}' was already enabled; falling back to reload.",
                    installedPack,
                    exception
            );
        }

        return reloadInstalledPack(server, installedPack);
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

    private static CompletableFuture<?> reloadInstalledPack(MinecraftServer server, Path installedPack) {
        try {
            Object repository = invokeNoArg(server, "getPackRepository");
            if (repository == null) {
                throw new IllegalStateException("MinecraftServer#getPackRepository was unavailable");
            }

            Set<String> before = stringSet(invokeNoArg(repository, "getAvailableIds"));
            invokeNoArg(repository, "reload");
            Set<String> available = stringSet(invokeNoArg(repository, "getAvailableIds"));
            Set<String> selected = stringSet(invokeNoArg(repository, "getSelectedIds"));

            String packId = findInstalledPackId(before, available, installedPack.getFileName().toString());
            if (packId == null) {
                throw new IllegalStateException(
                        "Installed datapack was not exposed by PackRepository: " + installedPack.getFileName()
                );
            }

            LinkedHashSet<String> requested = new LinkedHashSet<>(selected);
            requested.add(packId);

            Object result = invokeReloadResources(server, requested);
            if (result instanceof CompletableFuture<?> future) {
                future.whenComplete((ignored, error) -> {
                    if (error == null) {
                        DAI_Core.LOGGER.info(
                                "<DAI>: Experience datapack '{}' enabled and reloaded for the launched save.",
                                packId
                        );
                    } else {
                        DAI_Core.LOGGER.error(
                                "<DAI>: Experience datapack '{}' failed to reload.",
                                packId,
                                error
                        );
                    }
                });
                return future;
            }

            DAI_Core.LOGGER.info(
                    "<DAI>: Experience datapack '{}' enabled for the launched save.",
                    packId
            );
            return CompletableFuture.completedFuture(null);
        } catch (Throwable exception) {
            DAI_Core.LOGGER.error(
                    "<DAI>: Could not enable the copied experience datapack in the running save.",
                    exception
            );
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
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

        for (String id : available) {
            if (!before.contains(id)) return id;
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
