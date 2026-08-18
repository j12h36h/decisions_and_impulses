package io.github.j12h36h.dai.client.branding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Bridges MAIN-experience branding into FancyModLoader's official early-display
 * theme system. FML loads these files before normal mods initialize, so changes
 * prepared during this JVM become visible on the next launch.
 *
 * DAI owns only the theme files while a branded MAIN experience is active. If
 * the player already had custom FML theme files, they are backed up once and
 * restored when DAI early branding is disabled or no MAIN experience remains.
 */
public final class DAI_FmlEarlyBranding {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> THEME_FILES = List.of(
            "theme-default.json",
            "theme-darkmode.json",
            "theme-april-fools.json",
            "theme-april-fools-darkmode.json"
    );
    private static final String BACKGROUND_FILE = "dai_startup_background.png";
    private static final String LOGO_FILE = "dai_startup_logo.png";
    private static final String ICON_FILE = "dai_window_icon.png";

    private static String lastKey = "";

    private DAI_FmlEarlyBranding() {}

    public static void sync(DAI_ExperienceDefinition experience, Path companionPack) {
        DAI_ExperienceDefinition.Branding branding = experience == null
                ? DAI_ExperienceDefinition.Branding.DEFAULT
                : experience.branding();
        DAI_ExperienceDefinition.EarlyLoading early = branding.earlyLoading();

        long modified = modified(companionPack);
        String key = experience == null
                ? "none"
                : experience.id() + "|" + early.hashCode() + "|" + branding.hashCode() + "|" + modified;
        if (key.equals(lastKey)) return;
        lastKey = key;

        try {
            if (experience == null || !early.enabled() || companionPack == null) {
                restoreUserThemes();
                return;
            }

            Path fml = fmlThemeDirectory();
            Files.createDirectories(fml);
            backupUserThemesIfNeeded();

            byte[] background = readResource(companionPack, early.backgroundTexture());
            byte[] logo = readResource(companionPack, early.logo());
            byte[] icon = branding.useResourcePackIcon()
                    ? DAI_ClientBranding.readPackEntry(companionPack, "pack.png")
                    : null;

            writeOrDelete(fml.resolve(BACKGROUND_FILE), background);
            writeOrDelete(fml.resolve(LOGO_FILE), logo);
            writeOrDelete(fml.resolve(ICON_FILE), icon);

            JsonObject theme = buildTheme(branding, early, background != null, logo != null, icon != null);
            validateTheme(theme);
            String json = GSON.toJson(theme) + System.lineSeparator();
            for (String filename : THEME_FILES) {
                Files.writeString(fml.resolve(filename), json, StandardCharsets.UTF_8);
            }

            JsonObject state = new JsonObject();
            state.addProperty("managed", true);
            state.addProperty("experience", experience.id());
            state.addProperty("companion_modified", modified);
            Files.createDirectories(statePath().getParent());
            Files.writeString(statePath(), GSON.toJson(state) + System.lineSeparator(), StandardCharsets.UTF_8);

            DAI_Core.LOGGER.info(
                    "<DAI>: Prepared FML early-loading branding for MAIN experience '{}' (visible next launch).",
                    experience.id()
            );
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not synchronize FancyModLoader early branding.", exception);
        }
    }

    private static JsonObject buildTheme(
            DAI_ExperienceDefinition.Branding branding,
            DAI_ExperienceDefinition.EarlyLoading early,
            boolean hasBackground,
            boolean hasLogo,
            boolean hasIcon
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("extends", "builtin:default");
        if (hasIcon) root.addProperty("windowIcon", ICON_FILE);

        JsonObject colors = new JsonObject();
        colors.addProperty("screenBackground", htmlColor(branding.loadingBackground()));
        colors.addProperty("text", htmlColor(branding.loadingForeground()));
        root.add("colorScheme", colors);

        JsonObject loading = new JsonObject();

        JsonObject background = new JsonObject();
        background.addProperty("type", "image");
        background.addProperty("visible", hasBackground);
        background.addProperty("maintainAspectRatio", false);
        background.addProperty("left", 0);
        background.addProperty("top", 0);
        background.addProperty("right", 0);
        background.addProperty("bottom", 0);
        if (hasBackground) background.add("texture", stretchTexture(BACKGROUND_FILE, 854, 480));
        loading.add("background", background);

        loading.add("mojangLogo", visibility(!early.hideMojangLogo()));
        loading.add("startupLog", visibility(early.showStartupLog()));
        loading.add("performance", visibility(early.showPerformance()));

        JsonObject progress = visibility(early.showProgress());
        if (early.showProgress()) {
            progress.addProperty("maintainAspectRatio", false);
            progress.addProperty("left", 190);
            progress.addProperty("right", 190);
            progress.addProperty("bottom", 58);
        }
        loading.add("progressBars", progress);

        JsonObject decorations = new JsonObject();

        // Entries inside loadingScreen.decoration are polymorphic FML theme
        // elements. Unlike the predefined mojangLogo/startupLog/performance
        // elements, every decoration dictionary value MUST declare its type.
        // Omitting this caused FancyModLoader to reject the entire DAI theme
        // before Minecraft/DAI had even started.
        JsonObject version = new JsonObject();
        version.addProperty("type", "label");
        version.addProperty("text", "${version}");
        version.addProperty("visible", false);
        decorations.add("version", version);

        JsonObject fox = new JsonObject();
        fox.addProperty("type", "image");
        fox.addProperty("visible", false);
        // Keep the inherited resource valid even though the element is hidden.
        fox.add("texture", stretchTexture("fox_running.png", 151, 128));
        decorations.add("fox", fox);
        if (hasLogo) {
            JsonObject logo = new JsonObject();
            logo.addProperty("type", "image");
            logo.addProperty("visible", true);
            logo.addProperty("maintainAspectRatio", true);
            logo.addProperty("centerHorizontally", true);
            logo.addProperty("top", 112);
            int size = Math.max(48, Math.min(256, branding.loadingLogoSize()));
            logo.add("texture", stretchTexture(LOGO_FILE, size, size));
            decorations.add("daiLogo", logo);
        }
        loading.add("decoration", decorations);
        root.add("loadingScreen", loading);
        return root;
    }

    /** Minimal schema guard for the polymorphic FML fields DAI authors. */
    private static void validateTheme(JsonObject root) throws IOException {
        if (root == null || !root.has("version") || root.get("version").getAsInt() != 1) {
            throw new IOException("Generated FML theme is missing version=1");
        }
        if (!root.has("loadingScreen") || !root.get("loadingScreen").isJsonObject()) return;
        JsonObject loading = root.getAsJsonObject("loadingScreen");
        if (!loading.has("decoration") || !loading.get("decoration").isJsonObject()) return;
        JsonObject decoration = loading.getAsJsonObject("decoration");
        for (String key : decoration.keySet()) {
            if (!decoration.get(key).isJsonObject()) {
                throw new IOException("Generated FML decoration '" + key + "' is not an object");
            }
            JsonObject element = decoration.getAsJsonObject(key);
            if (!element.has("type") || element.get("type").getAsString().isBlank()) {
                throw new IOException("Generated FML decoration '" + key + "' is missing required type");
            }
        }
    }

    private static JsonObject visibility(boolean visible) {
        JsonObject object = new JsonObject();
        object.addProperty("visible", visible);
        return object;
    }

    private static JsonObject stretchTexture(String resource, int width, int height) {
        JsonObject texture = new JsonObject();
        texture.addProperty("resource", resource);
        JsonObject scaling = new JsonObject();
        scaling.addProperty("type", "stretch");
        scaling.addProperty("width", width);
        scaling.addProperty("height", height);
        scaling.addProperty("linearScaling", true);
        texture.add("scaling", scaling);
        return texture;
    }

    private static void backupUserThemesIfNeeded() throws IOException {
        if (Files.isRegularFile(statePath())) return;
        Path fml = fmlThemeDirectory();
        Path backup = backupDirectory();
        Files.createDirectories(backup);
        for (String filename : THEME_FILES) {
            Path source = fml.resolve(filename);
            Path target = backup.resolve(filename);
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void restoreUserThemes() throws IOException {
        if (!Files.isRegularFile(statePath())) return;
        Path fml = fmlThemeDirectory();
        Path backup = backupDirectory();
        Files.createDirectories(fml);

        for (String filename : THEME_FILES) {
            Path target = fml.resolve(filename);
            Path saved = backup.resolve(filename);
            if (Files.isRegularFile(saved)) {
                Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(target);
            }
        }

        Files.deleteIfExists(fml.resolve(BACKGROUND_FILE));
        Files.deleteIfExists(fml.resolve(LOGO_FILE));
        Files.deleteIfExists(fml.resolve(ICON_FILE));
        Files.deleteIfExists(statePath());
        deleteDirectoryIfEmpty(backup);
        deleteDirectoryIfEmpty(statePath().getParent());

        DAI_Core.LOGGER.info("<DAI>: Restored pre-DAI FancyModLoader early-loading theme files.");
    }

    private static void writeOrDelete(Path path, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            Files.deleteIfExists(path);
        } else {
            Files.write(path, bytes);
        }
    }

    private static byte[] readResource(Path companionPack, String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String value = identifier.trim().toLowerCase();
        int colon = value.indexOf(':');
        if (colon <= 0 || colon >= value.length() - 1) return null;
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        return DAI_ClientBranding.readPackEntry(companionPack, "assets/" + namespace + "/" + path);
    }

    private static String htmlColor(int argb) {
        return String.format("#%08X", argb);
    }

    private static long modified(Path path) {
        if (path == null) return 0L;
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    private static Path fmlThemeDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("fml").toAbsolutePath().normalize();
    }

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(DAI_Core.MODID)
                .resolve("branding")
                .resolve("fml_theme_state.json")
                .toAbsolutePath().normalize();
    }

    private static Path backupDirectory() {
        return statePath().getParent().resolve("fml_theme_backup");
    }

    private static void deleteDirectoryIfEmpty(Path path) {
        try (var stream = Files.list(path)) {
            if (stream.findAny().isEmpty()) Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Best effort only.
        }
    }
}
