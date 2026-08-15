package io.github.j12h36h.dai.packs;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * DAI-owned global datapack library.
 *
 * Vanilla keeps resource packs at the game-instance level but normally keeps
 * datapacks inside individual saves. DAI adds a sibling <game>/datapacks
 * library so experiences can be installed once, discovered at the title
 * screen, and handed off into the save that actually launches them.
 *
 * The directory is a library/source location, not a promise that every pack in
 * it is enabled in every world. Experience launch copies/enables the exact
 * declaring pack in the target save, preserving normal vanilla world-local
 * datapack ownership.
 */
public final class DAI_GlobalDatapackLibrary {

    private static volatile boolean initialized;

    private DAI_GlobalDatapackLibrary() {}

    /** Creates the library directory once and returns its normalized path. */
    public static synchronized Path initialize() {
        Path root = root();
        if (initialized) return root;

        try {
            Files.createDirectories(root);
            initialized = true;

            DAI_Core.LOGGER.info(
                    "<DAI>: Global datapack library ready at '{}' ({} pack candidate(s)).",
                    root,
                    countPackCandidates(root)
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Could not create global datapack library '{}'.",
                    root,
                    exception
            );
        }

        return root;
    }

    /** <gameDir>/datapacks, alongside Minecraft's normal resourcepacks folder. */
    public static Path root() {
        Path config = FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
        Path game = config.getParent() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : config.getParent();
        return game.resolve("datapacks").toAbsolutePath().normalize();
    }

    public static boolean contains(Path path) {
        if (path == null) return false;
        return path.toAbsolutePath().normalize().startsWith(root());
    }

    private static long countPackCandidates(Path root) {
        if (!Files.isDirectory(root)) return 0L;
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(DAI_GlobalDatapackLibrary::isPackCandidate).count();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static boolean isPackCandidate(Path path) {
        if (path == null) return false;
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve("pack.mcmeta"));
        }
        if (!Files.isRegularFile(path)) return false;
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }
}
