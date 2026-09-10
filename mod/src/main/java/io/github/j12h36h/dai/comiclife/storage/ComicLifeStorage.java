package io.github.j12h36h.dai.comiclife.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.j12h36h.dai.comiclife.model.ComicLifeArchive;
import io.github.j12h36h.dai.comiclife.util.Reflect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ComicLifeStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ComicLifeStorage() {}

    public static ComicLifeArchive load(String uuid) {
        Path file = file(uuid);
        if (!Files.isRegularFile(file)) return new ComicLifeArchive();
        try {
            ComicLifeArchive archive = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ComicLifeArchive.class);
            if (archive == null) archive = new ComicLifeArchive();
            archive.normalize();
            return archive;
        } catch (Exception exception) {
            System.err.println("[ComicLife] Could not read archive '" + file + "': " + exception.getMessage());
            return new ComicLifeArchive();
        }
    }

    public static synchronized void save(String uuid, ComicLifeArchive archive) {
        if (uuid == null || uuid.isBlank() || archive == null) return;
        archive.normalize();
        Path file = file(uuid);
        Path temp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, GSON.toJson(archive), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicUnsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            System.err.println("[ComicLife] Could not save archive '" + file + "': " + exception.getMessage());
            try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    public static Path root() {
        Object gameDirEntry = Reflect.staticField("net.neoforged.fml.loading.FMLPaths", "GAMEDIR");
        Object gameDir = Reflect.invoke(gameDirEntry, "get");
        Path base = gameDir instanceof Path p ? p : Path.of(System.getProperty("user.dir", "."));
        return base.toAbsolutePath().normalize().resolve("comiclife");
    }

    private static Path file(String uuid) {
        String safe = uuid == null ? "unknown" : uuid.replaceAll("[^a-zA-Z0-9._-]", "_");
        return root().resolve(safe + ".json");
    }
}
