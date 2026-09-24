package io.github.j12h36h.dai.client.branding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.config.DAI_ClientConfig;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.neoforged.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Installs DAI's engine-owned FancyModLoader splash theme.
 *
 * DAI 4.3 intentionally ignores Experience branding here. FML exists before a
 * gameplay world exists, so this phase belongs exclusively to the engine. The
 * signature is retained so 4.2 callers remain binary/source compatible.
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
    private static String lastKey = "";

    private DAI_FmlEarlyBranding() {}

    public static void sync(DAI_ExperienceDefinition ignoredExperience, Path ignoredCompanionPack) {
        boolean enabled = DAI_ClientConfig.fullGameShell() && DAI_ClientConfig.loadingScreens();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        String key = enabled ? "dai43|" + profile.id() + "|" + profile.hashCode() : "disabled";
        if (key.equals(lastKey)) return;
        lastKey = key;

        try {
            if (!enabled) {
                restoreUserThemes();
                return;
            }

            Path fml = fmlThemeDirectory();
            Files.createDirectories(fml);
            backupUserThemesIfNeeded();

            byte[] background = renderDaiBackground(profile);
            if (background == null || background.length == 0) {
                Files.deleteIfExists(fml.resolve(BACKGROUND_FILE));
            } else {
                Files.write(fml.resolve(BACKGROUND_FILE), background);
            }

            JsonObject theme = buildTheme(profile, background != null && background.length > 0);
            validateTheme(theme);
            String json = GSON.toJson(theme) + System.lineSeparator();
            for (String filename : THEME_FILES) {
                Files.writeString(fml.resolve(filename), json, StandardCharsets.UTF_8);
            }

            JsonObject state = new JsonObject();
            state.addProperty("managed", true);
            state.addProperty("owner", "dai_engine");
            state.addProperty("feature_level", DAI_Core.FEATURE_LEVEL);
            Files.createDirectories(statePath().getParent());
            Files.writeString(statePath(), GSON.toJson(state) + System.lineSeparator(), StandardCharsets.UTF_8);
            DAI_Core.LOGGER.info("<DAI>: Prepared engine-owned FML intro splash (visible next launch).");
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not synchronize FancyModLoader DAI splash.", exception);
        }
    }

    private static byte[] renderDaiBackground(DAI_PresentationProfileService.Profile profile) {
        try {
            final int width = 854;
            final int height = 480;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color top = color(profile.backgroundTop(), new Color(8, 11, 22));
            Color bottom = color(profile.backgroundBottom(), new Color(22, 10, 32));
            Color primary = color(profile.primary(), new Color(255, 163, 60));
            Color secondary = color(profile.secondary(), new Color(168, 85, 247));
            Color text = color(profile.text(), new Color(244, 247, 255));

            g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
            g.fillRect(0, 0, width, height);

            int cx = width / 2;
            int cy = 222;
            g.setColor(withAlpha(secondary, 80));
            drawDiamond(g, cx, cy, 182, 72, 3);
            g.setColor(withAlpha(primary, 60));
            drawDiamond(g, cx, cy, 238, 106, 2);

            // Converging data lanes / horizon grid.
            g.setColor(withAlpha(secondary, 45));
            for (int i = -6; i <= 6; i++) {
                g.drawLine(cx + i * 72, height, cx + i * 9, cy + 75);
            }
            for (int y = cy + 84; y < height; y += 34) {
                int inset = Math.max(0, (height - y) / 2);
                g.drawLine(inset, y, width - inset, y);
            }

            // Four launcher-space satellite nodes.
            drawNode(g, cx - 182, cy, secondary);
            drawNode(g, cx + 182, cy, primary);
            drawNode(g, cx, cy - 72, new Color(86, 198, 216));
            drawNode(g, cx, cy + 72, new Color(101, 214, 155));

            // Central DAI mark, rendered as geometry rather than font text so
            // the FML image and live primitive splash share the same identity.
            g.setColor(withAlpha(new Color(12, 10, 25), 190));
            g.fillRoundRect(cx - 132, cy - 58, 264, 116, 18, 18);
            g.setColor(text);
            drawDaiMark(g, cx, cy, 7);

            g.setPaint(new GradientPaint(0, 370, new Color(0, 0, 0, 0), 0, height, new Color(5, 5, 12, 190)));
            g.fillRect(0, 370, width, 110);
            g.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not generate early DAI splash art.", exception);
            return null;
        }
    }

    private static JsonObject buildTheme(DAI_PresentationProfileService.Profile profile, boolean hasBackground) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("extends", "builtin:default");

        JsonObject colors = new JsonObject();
        colors.addProperty("screenBackground", hex(profile.backgroundTop()));
        colors.addProperty("text", hex(profile.text()));
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
        loading.add("mojangLogo", visibility(false));
        loading.add("startupLog", visibility(false));
        loading.add("performance", visibility(false));
        loading.add("progressBars", visibility(false));

        JsonObject decorations = new JsonObject();
        JsonObject version = new JsonObject();
        version.addProperty("type", "label");
        version.addProperty("text", "${version}");
        version.addProperty("visible", false);
        decorations.add("version", version);
        JsonObject fox = new JsonObject();
        fox.addProperty("type", "image");
        fox.addProperty("visible", false);
        fox.add("texture", stretchTexture("fox_running.png", 151, 128));
        decorations.add("fox", fox);
        loading.add("decoration", decorations);
        root.add("loadingScreen", loading);
        return root;
    }

    private static void validateTheme(JsonObject root) throws IOException {
        if (root == null || !root.has("version") || root.get("version").getAsInt() != 1) {
            throw new IOException("Generated FML theme is missing version=1");
        }
        JsonObject loading = root.getAsJsonObject("loadingScreen");
        if (loading == null || !loading.has("decoration")) return;
        JsonObject decoration = loading.getAsJsonObject("decoration");
        for (String key : decoration.keySet()) {
            JsonObject element = decoration.getAsJsonObject(key);
            if (element == null || !element.has("type") || element.get("type").getAsString().isBlank()) {
                throw new IOException("Generated FML decoration '" + key + "' is missing required type");
            }
        }
    }

    private static void drawDiamond(Graphics2D g, int cx, int cy, int rx, int ry, int stroke) {
        for (int i = 0; i < stroke; i++) {
            g.drawLine(cx, cy - ry - i, cx + rx + i, cy);
            g.drawLine(cx + rx + i, cy, cx, cy + ry + i);
            g.drawLine(cx, cy + ry + i, cx - rx - i, cy);
            g.drawLine(cx - rx - i, cy, cx, cy - ry - i);
        }
    }

    private static void drawNode(Graphics2D g, int cx, int cy, Color color) {
        g.setColor(withAlpha(new Color(12, 10, 25), 170));
        g.fillRoundRect(cx - 22, cy - 22, 44, 44, 10, 10);
        g.setColor(color);
        g.fillRoundRect(cx - 13, cy - 13, 26, 26, 7, 7);
        g.setColor(new Color(17, 23, 37));
        g.fillRoundRect(cx - 6, cy - 6, 12, 12, 4, 4);
    }

    private static void drawDaiMark(Graphics2D g, int cx, int cy, int u) {
        int stroke = u * 2;
        int h = u * 12;
        int top = cy - h / 2;
        int bottom = cy + h / 2;
        int x = cx - u * 14;

        g.fillRect(x, top, stroke, h);
        g.fillRect(x, top, u * 7, stroke);
        g.fillRect(x, bottom - stroke, u * 7, stroke);
        g.fillRect(x + u * 6, top + stroke, stroke, h - stroke * 2);

        x = cx - u * 2;
        g.fillRect(x, top + stroke, stroke, h - stroke);
        g.fillRect(x + u * 6, top + stroke, stroke, h - stroke);
        g.fillRect(x + stroke, top, u * 6 - stroke, stroke);
        g.fillRect(x + stroke, cy - stroke / 2, u * 6 - stroke, stroke);

        x = cx + u * 10;
        g.fillRect(x, top, u * 7, stroke);
        g.fillRect(x + u * 3, top, stroke, h);
        g.fillRect(x, bottom - stroke, u * 7, stroke);
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
            if (Files.isRegularFile(saved)) Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.deleteIfExists(target);
        }
        Files.deleteIfExists(fml.resolve(BACKGROUND_FILE));
        Files.deleteIfExists(statePath());
        deleteDirectoryIfEmpty(backup);
        deleteDirectoryIfEmpty(statePath().getParent());
        DAI_Core.LOGGER.info("<DAI>: Restored pre-DAI FancyModLoader theme files.");
    }

    private static Path fmlThemeDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("fml").toAbsolutePath().normalize();
    }

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get().resolve(DAI_Core.MODID).resolve("branding")
                .resolve("fml_theme_state.json").toAbsolutePath().normalize();
    }

    private static Path backupDirectory() {
        return statePath().getParent().resolve("fml_theme_backup");
    }

    private static void deleteDirectoryIfEmpty(Path path) {
        if (path == null || !Files.isDirectory(path)) return;
        try (var stream = Files.list(path)) {
            if (stream.findAny().isEmpty()) Files.deleteIfExists(path);
        } catch (Exception ignored) { }
    }

    private static Color color(int argb, Color fallback) {
        if ((argb & 0x00FFFFFF) == 0 && (argb >>> 24) == 0) return fallback;
        return new Color(argb, true);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
