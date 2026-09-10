package io.github.j12h36h.dai.story.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.story.model.DAI_StoryArchive;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Generic persistent storage for pack-defined story archives. */
public final class DAI_StoryStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private DAI_StoryStorage() {}

    public static DAI_StoryArchive load(String profile, String uuid) {
        Path file = path(profile, uuid);
        if (!Files.isRegularFile(file)) return fresh(profile);
        try {
            DAI_StoryArchive archive = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), DAI_StoryArchive.class);
            if (archive == null) archive = fresh(profile);
            archive.profile = profile == null ? "" : profile;
            archive.normalize();
            return archive;
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not read story archive '{}'.", file, exception);
            return fresh(profile);
        }
    }

    public static synchronized void save(String profile, String uuid, DAI_StoryArchive archive) {
        if (uuid == null || uuid.isBlank() || archive == null) return;
        Path file = path(profile, uuid);
        try {
            Files.createDirectories(file.getParent());
            archive.profile = profile == null ? "" : profile;
            archive.normalize();
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(archive), StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (Exception ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not save story archive '{}'.", file, exception);
        }
    }

    private static DAI_StoryArchive fresh(String profile) {
        DAI_StoryArchive archive = new DAI_StoryArchive();
        archive.profile = profile == null ? "" : profile;
        return archive;
    }

    private static Path path(String profile, String uuid) {
        String profileKey = clean(profile);
        String userKey = clean(uuid);
        return FMLPaths.GAMEDIR.get().resolve("dai_data").resolve("story_archives")
                .resolve(profileKey.isBlank() ? "default" : profileKey)
                .resolve((userKey.isBlank() ? "player" : userKey) + ".json").toAbsolutePath().normalize();
    }

    private static String clean(String value) {
        String s = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "_");
        return s.length() > 128 ? s.substring(0, 128) : s;
    }
}
