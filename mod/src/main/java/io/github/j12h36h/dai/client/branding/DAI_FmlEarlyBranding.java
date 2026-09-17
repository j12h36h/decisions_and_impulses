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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        boolean experienceOwns = experience != null && early.enabled() && companionPack != null;
        boolean daiFallback = !experienceOwns && DAI_ClientConfig.loadingScreens();
        DAI_PresentationProfileService.Profile fallbackProfile = DAI_PresentationProfileService.selected();

        long modified = modified(companionPack);
        String key = experienceOwns
                ? "experience|" + experience.id() + "|" + early.hashCode() + "|" + branding.hashCode() + "|" + modified
                : (daiFallback ? "presentation|" + fallbackProfile.id() + "|" + fallbackProfile.hashCode() : "disabled");
        if (key.equals(lastKey)) return;
        lastKey = key;

        try {
            if (!experienceOwns && !daiFallback) {
                restoreUserThemes();
                return;
            }

            Path fml = fmlThemeDirectory();
            Files.createDirectories(fml);
            backupUserThemesIfNeeded();

            byte[] background;
            byte[] logo;
            byte[] icon;
            JsonObject theme;
            String owner;

            if (experienceOwns) {
                background = readResource(companionPack, early.backgroundTexture());
                logo = readResource(companionPack, early.logo());
                icon = branding.useResourcePackIcon()
                        ? DAI_ClientBranding.readPackEntry(companionPack, "pack.png")
                        : null;
                theme = buildTheme(branding, early, background != null, logo != null, icon != null);
                owner = experience.id();
            } else {
                DAI_PresentationProfileService.Profile profile = fallbackProfile;
                background = defaultVillageSplashBackground(profile);
                logo = null;
                icon = null;
                theme = buildDaiFallbackTheme(background != null, profile);
                owner = "presentation:" + profile.id();
            }

            writeOrDelete(fml.resolve(BACKGROUND_FILE), background);
            writeOrDelete(fml.resolve(LOGO_FILE), logo);
            writeOrDelete(fml.resolve(ICON_FILE), icon);

            validateTheme(theme);
            String json = GSON.toJson(theme) + System.lineSeparator();
            for (String filename : THEME_FILES) {
                Files.writeString(fml.resolve(filename), json, StandardCharsets.UTF_8);
            }

            JsonObject state = new JsonObject();
            state.addProperty("managed", true);
            state.addProperty("experience", owner);
            state.addProperty("companion_modified", modified);
            Files.createDirectories(statePath().getParent());
            Files.writeString(statePath(), GSON.toJson(state) + System.lineSeparator(), StandardCharsets.UTF_8);

            if (experienceOwns) {
                DAI_Core.LOGGER.info(
                        "<DAI>: Prepared FML early-loading branding for MAIN experience '{}' (visible next launch).",
                        experience.id()
                );
            } else {
                DAI_Core.LOGGER.info("<DAI>: Prepared DAI village FML early-loading fallback (visible next launch).");
            }
        } catch (Exception exception) {
            DAI_Core.LOGGER.warn("<DAI>: Could not synchronize FancyModLoader early branding.", exception);
        }
    }

    /**
     * FancyModLoader runs before ordinary mod render hooks exist. DAI can still
     * prepare its supported theme files for the next JVM launch; the animated
     * rings/realtime percentage take over once Minecraft's LoadingOverlay is live.
     */
    private static JsonObject buildDaiFallbackTheme(boolean hasBackground, DAI_PresentationProfileService.Profile profile) {
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

    /**
     * Generates the registry-independent splash that FancyModLoader can show
     * before Minecraft resources or block-model registries exist. Keep this
     * composition visually aligned with the live DAI loading village so the
     * handoff feels like one scene becoming three-dimensional instead of a
     * theme swap.
     */
    private static byte[] defaultVillageSplashBackground(DAI_PresentationProfileService.Profile profile) {
        try {
            final int width = 854;
            final int height = 480;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();

            // Deliberately preserve hard pixel/block edges for Minecraft-like
            // splash art. The sky alone uses a soft vertical gradient.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            int horizon = 270;
            g.setPaint(new GradientPaint(0, 0, new Color(0x6DB8E8), 0, horizon, new Color(0xC7E7F6)));
            g.fillRect(0, 0, width, horizon);

            // Square sun + block clouds.
            g.setColor(new Color(0xFFF0A0));
            g.fillRect(766, 38, 42, 42);
            drawCloud(g, 72, 72, 5);
            drawCloud(g, 470, 48, 4);
            drawCloud(g, 665, 105, 3);

            // Distant stepped hills.
            drawSteppedHill(g, -40, 236, 300, 0x6E9A57);
            drawSteppedHill(g, 170, 225, 380, 0x79A85F);
            drawSteppedHill(g, 510, 242, 380, 0x628B4E);

            // Main ground layers.
            g.setColor(new Color(0x72A94F));
            g.fillRect(0, horizon, width, height - horizon);
            g.setColor(new Color(0x679948));
            g.fillRect(0, 346, width, height - 346);

            // Mid-ground village, intentionally arranged around the same
            // central well/road composition as the live 3-D loading scene.
            drawVillageHouse(g, 74, 222, 104, 72, 0xD7B675, 0x7A4928);
            drawVillageHouse(g, 196, 202, 126, 86, 0xCFA968, 0x684026);
            drawVillageHouse(g, 548, 207, 126, 82, 0xD8B775, 0x754528);
            drawVillageHouse(g, 690, 229, 92, 65, 0xC9A565, 0x6A4025);
            drawVillageTower(g, 357, 187);

            // Central well and perspective road.
            drawWell(g, 401, 262, 8);
            drawPerspectiveRoad(g, width, height, 426, 289);

            // Foreground farm, market, pond, fences and natural detail.
            drawFarm(g, 32, 326, 246, 104);
            drawMarket(g, 641, 318, 6);
            drawPond(g, 516, 370, 5);
            drawFenceLine(g, 280, 371, 108, 5);
            drawHayStack(g, 742, 385, 5);
            drawTree(g, 705, 264, 8);
            drawTree(g, 300, 262, 5);
            drawTree(g, 810, 282, 4);
            drawBush(g, 266, 307, 5);
            drawBush(g, 575, 319, 4);
            drawLamp(g, 337, 312, 5);
            drawLamp(g, 520, 309, 5);

            // Deterministic grass, flowers, path stones and field clutter.
            for (int i = 0; i < 112; i++) {
                int x = Math.floorMod(i * 157 + 31, width);
                int y = 306 + Math.floorMod(i * 71 + 13, 120);
                if (x > 300 && x < 565) continue;
                int detailColor = (i % 17 == 0) ? 0xD85B5B
                        : ((i % 13 == 0) ? 0x6A8DD8
                        : ((i % 9 == 0) ? 0xE8D953
                        : ((i % 7 == 0) ? 0xE7E7E2 : 0x4F873A)));
                g.setColor(new Color(detailColor));
                g.fillRect(x, y, (i % 4 == 0) ? 4 : 2, (i % 3 == 0) ? 5 : 3);
            }
            // Bottom vignette keeps FML text/progress legible if users enable
            // those elements in a custom theme while preserving the artwork.
            g.setPaint(new GradientPaint(0, 385, new Color(0, 0, 0, 0), 0, height, new Color(18, 28, 16, 115)));
            g.fillRect(0, 385, width, height - 385);

            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (Exception exception) {
            DAI_Core.LOGGER.debug("<DAI>: Could not render fallback early-loading village background.", exception);
            return null;
        }
    }

    private static void drawCloud(Graphics2D g, int x, int y, int scale) {
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRect(x, y + scale * 2, scale * 13, scale * 4);
        g.fillRect(x + scale * 3, y, scale * 6, scale * 6);
        g.fillRect(x + scale * 8, y + scale, scale * 4, scale * 5);
    }

    private static void drawSteppedHill(Graphics2D g, int x, int baseline, int span, int rgb) {
        g.setColor(new Color(rgb));
        int step = 12;
        int count = Math.max(1, span / step);
        for (int i = 0; i < count; i++) {
            double n = i / (double)Math.max(1, count - 1);
            double arch = 1.0D - Math.abs(n * 2.0D - 1.0D);
            int top = baseline - (int)Math.round(arch * 44.0D);
            g.fillRect(x + i * step, top, step + 1, 300 - top);
        }
    }

    private static void drawVillageHouse(Graphics2D g, int x, int y, int w, int h, int wallRgb, int roofRgb) {
        final int roofRows = 6;
        final int roofStepY = 4;
        final int wallTop = y + roofRows * roofStepY;
        final int bottom = y + h;
        final int cx = x + w / 2;

        // Foundation + shaded wall panels.
        g.setColor(new Color(wallRgb));
        g.fillRect(x, wallTop, w, Math.max(1, bottom - wallTop));
        g.setColor(new Color(0xB48C58));
        g.fillRect(x + 7, wallTop + 7, Math.max(1, w / 2 - 7), Math.max(1, bottom - wallTop - 14));
        g.setColor(new Color(0x77736A));
        g.fillRect(x, bottom - 7, w, 7);
        g.setColor(new Color(0x929087));
        for (int px = x; px < x + w; px += 14) g.fillRect(px, bottom - 7, Math.min(7, x + w - px), 7);

        // Correctly pitched roof: narrow ridge, progressively wider lower rows.
        int ridgeHalf = Math.max(10, w / 9);
        int eaveHalf = w / 2 + 8;
        for (int r = 0; r < roofRows; r++) {
            float t = r / (float)Math.max(1, roofRows - 1);
            int half = Math.round(ridgeHalf + (eaveHalf - ridgeHalf) * t);
            g.setColor(new Color((r & 1) == 0 ? roofRgb : brighten(roofRgb, 18)));
            g.fillRect(cx - half, y + r * roofStepY, half * 2, roofStepY + 2);
        }
        g.setColor(new Color(darken(roofRgb, 28)));
        g.fillRect(cx - eaveHalf, wallTop - 2, eaveHalf * 2, 4);
        g.fillRect(cx - ridgeHalf, y, ridgeHalf * 2, 3);

        // Chimney and timber framing.
        int chimneyX = x + Math.round(w * 0.70F);
        g.setColor(new Color(0x77736A));
        g.fillRect(chimneyX, y + 4, 12, Math.max(10, wallTop - y + 2));
        g.setColor(new Color(0x5A3821));
        g.fillRect(x, wallTop, 7, Math.max(1, bottom - wallTop));
        g.fillRect(x + w - 7, wallTop, 7, Math.max(1, bottom - wallTop));
        g.fillRect(cx - 3, wallTop, 6, Math.max(1, bottom - wallTop));
        int beamY = wallTop + Math.max(10, (bottom - wallTop) / 2);
        g.fillRect(x, beamY, w, 4);

        // Window modules with shutters and warm interior reflection.
        int windowW = Math.max(18, w / 5);
        int windowH = Math.max(13, (bottom - wallTop) / 4);
        int windowY = wallTop + 12;
        drawWindow(g, x + 16, windowY, windowW, windowH);
        drawWindow(g, x + w - 16 - windowW, windowY, windowW, windowH);

        // Door + lintel + handle.
        int doorW = Math.max(15, w / 7);
        int doorX = cx - doorW / 2;
        int doorY = bottom - Math.max(28, (bottom - wallTop) / 2);
        g.setColor(new Color(0x81502B));
        g.fillRect(doorX, doorY, doorW, bottom - doorY);
        g.setColor(new Color(0x5A3821));
        g.fillRect(doorX - 3, doorY - 3, doorW + 6, 3);
        g.setColor(new Color(0xE7D98B));
        g.fillRect(doorX + doorW - 5, doorY + 13, 2, 2);

        // Window flower boxes.
        g.setColor(new Color(0x754A2A));
        g.fillRect(x + 16, windowY + windowH + 2, windowW, 4);
        g.fillRect(x + w - 16 - windowW, windowY + windowH + 2, windowW, 4);
        g.setColor(new Color(0xD85B5B));
        for (int i = 0; i < 3; i++) {
            g.fillRect(x + 20 + i * 7, windowY + windowH - 1, 3, 3);
            g.fillRect(x + w - 16 - windowW + 4 + i * 7, windowY + windowH - 1, 3, 3);
        }
    }

    private static void drawWindow(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x6D4829));
        g.fillRect(x - 4, y, 4, h);
        g.fillRect(x + w, y, 4, h);
        g.setColor(new Color(0x91D4E7));
        g.fillRect(x, y, w, h);
        g.setColor(new Color(0xE9D989));
        g.fillRect(x + 2, y + 2, Math.max(3, w / 3), Math.max(3, h / 3));
        g.setColor(new Color(0x6D4829));
        g.fillRect(x + w / 2, y, 2, h);
        g.fillRect(x, y + h / 2, w, 2);
    }

    private static void drawVillageTower(Graphics2D g, int x, int y) {
        int w = 68;
        int roofRows = 7;
        int stepY = 4;
        int wallTop = y + roofRows * stepY;
        int bottom = y + 116;
        int cx = x + w / 2;

        g.setColor(new Color(0xB7A06D));
        g.fillRect(x, wallTop, w, bottom - wallTop);
        g.setColor(new Color(0xA48C5F));
        g.fillRect(x + 7, wallTop + 7, w / 2 - 7, bottom - wallTop - 14);
        g.setColor(new Color(0x66503A));
        g.fillRect(x, wallTop, 6, bottom - wallTop);
        g.fillRect(x + w - 6, wallTop, 6, bottom - wallTop);
        g.fillRect(cx - 2, wallTop, 4, bottom - wallTop);

        int ridgeHalf = 10;
        int eaveHalf = w / 2 + 10;
        for (int r = 0; r < roofRows; r++) {
            float t = r / (float)Math.max(1, roofRows - 1);
            int half = Math.round(ridgeHalf + (eaveHalf - ridgeHalf) * t);
            g.setColor(new Color((r & 1) == 0 ? 0x6B4226 : 0x835334));
            g.fillRect(cx - half, y + r * stepY, half * 2, stepY + 2);
        }
        g.setColor(new Color(0x4D2F1D));
        g.fillRect(cx - eaveHalf, wallTop - 2, eaveHalf * 2, 4);

        // Bell opening and lower glass window.
        g.setColor(new Color(0x4D2F1D));
        g.fillRect(x + 18, wallTop + 12, 32, 22);
        g.setColor(new Color(0xD8A83D));
        g.fillRect(cx - 6, wallTop + 17, 12, 12);
        g.setColor(new Color(0x89CAE0));
        g.fillRect(x + 22, wallTop + 43, 24, 19);
        g.setColor(new Color(0x66503A));
        g.fillRect(x + 29, bottom - 32, 12, 32);
    }

    private static void drawWell(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0x7B7B76));
        g.fillRect(x, y + b * 5, b * 8, b * 4);
        g.setColor(new Color(0x408FCC));
        g.fillRect(x + b, y + b * 6, b * 6, b);
        g.setColor(new Color(0x5C4A39));
        g.fillRect(x + b, y + b, b, b * 5);
        g.fillRect(x + b * 6, y + b, b, b * 5);
        g.setColor(new Color(0x795338));
        g.fillRect(x, y, b * 8, b * 2);
    }

    private static void drawPerspectiveRoad(Graphics2D g, int width, int height, int cx, int yTop) {
        for (int y = yTop; y < height; y += 5) {
            float t = (y - yTop) / (float)Math.max(1, height - yTop);
            int half = Math.round(14 + t * 150);
            g.setColor(new Color(((y / 5) % 4 == 0) ? 0x9E8B60 : 0xB7A16F));
            g.fillRect(cx - half, y, half * 2, 6);
        }
        g.setColor(new Color(0x7E765C));
        for (int i = 0; i < 12; i++) {
            int y = yTop + 20 + i * 14;
            float t = (y - yTop) / (float)Math.max(1, height - yTop);
            int x = cx - Math.round(10 + t * 100) + (i % 3) * 18;
            g.fillRect(x, y, 5 + i / 3, 3);
        }
    }

    private static void drawFarm(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x724C2B));
        g.fillRect(x, y, w, h);
        for (int row = 0; row < 5; row++) {
            int ry = y + 10 + row * 17;
            if (row == 2) {
                g.setColor(new Color(0x3F7592));
                g.fillRect(x + 8, ry, w - 16, 6);
                continue;
            }
            for (int px = x + 12; px < x + w - 10; px += 18) {
                g.setColor(new Color((row & 1) == 0 ? 0xE3C84E : 0x8FB442));
                g.fillRect(px, ry - 9, 5, 13);
                g.fillRect(px - 3, ry - 5, 11, 4);
            }
        }
        g.setColor(new Color(0x976538));
        g.fillRect(x, y - 5, w, 5);
        g.fillRect(x, y + h, w, 5);
        for (int px = x; px <= x + w; px += 24) g.fillRect(px, y - 10, 5, h + 20);
    }

    private static void drawMarket(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0x62452F));
        g.fillRect(x, y, b, b * 11);
        g.fillRect(x + b * 15, y, b, b * 11);
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color((i & 1) == 0 ? 0xB84A3C : 0xF2DDC1));
            g.fillRect(x + i * b * 2, y, b * 2, b * 3);
        }
        g.setColor(new Color(0x9A6938));
        g.fillRect(x + b * 2, y + b * 7, b * 5, b * 4);
        g.fillRect(x + b * 9, y + b * 7, b * 5, b * 4);
        g.setColor(new Color(0xD59A44));
        g.fillRect(x + b * 3, y + b * 6, b, b);
        g.fillRect(x + b * 10, y + b * 6, b, b);
    }

    private static void drawPond(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0xD7C48A));
        g.fillRect(x, y, b * 18, b * 7);
        g.setColor(new Color(0x4E9BCC));
        g.fillRect(x + b, y + b, b * 16, b * 5);
        g.setColor(new Color(0x75B9DB));
        g.fillRect(x + b * 3, y + b, b * 8, b);
        g.setColor(new Color(0x6F9A42));
        for (int i = 0; i < 5; i++) {
            int rx = x + b * (2 + i * 3);
            g.fillRect(rx, y - b * (1 + i % 2), Math.max(2, b / 2), b * (2 + i % 2));
        }
    }

    private static void drawFenceLine(Graphics2D g, int x, int y, int width, int b) {
        g.setColor(new Color(0x91623A));
        g.fillRect(x, y, width, Math.max(2, b / 2));
        g.fillRect(x, y + b * 2, width, Math.max(2, b / 2));
        for (int px = x; px <= x + width; px += b * 4) {
            g.fillRect(px, y - b, b, b * 5);
        }
    }

    private static void drawHayStack(Graphics2D g, int x, int y, int b) {
        for (int row = 0; row < 3; row++) {
            int count = 3 - row;
            int offset = row * b * 2;
            for (int i = 0; i < count; i++) {
                int bx = x + offset + i * b * 4;
                int by = y - row * b * 3;
                g.setColor(new Color(0xD5A83A));
                g.fillRect(bx, by, b * 4, b * 3);
                g.setColor(new Color(0x9B6D29));
                g.fillRect(bx, by + b, b * 4, Math.max(2, b / 2));
            }
        }
    }

    private static void drawBush(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0x477C38));
        g.fillRect(x, y + b, b * 6, b * 4);
        g.setColor(new Color(0x5A9844));
        g.fillRect(x + b, y, b * 4, b * 4);
        g.setColor(new Color(0xE7D64E));
        g.fillRect(x + b, y + b, 2, 2);
        g.fillRect(x + b * 4, y + b * 2, 2, 2);
    }

    private static int brighten(int rgb, int amount) {
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (rgb & 0xFF) + amount);
        return (r << 16) | (g << 8) | b;
    }

    private static int darken(int rgb, int amount) {
        int r = Math.max(0, ((rgb >> 16) & 0xFF) - amount);
        int g = Math.max(0, ((rgb >> 8) & 0xFF) - amount);
        int b = Math.max(0, (rgb & 0xFF) - amount);
        return (r << 16) | (g << 8) | b;
    }

    private static void drawTree(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0x6A4527));
        g.fillRect(x + b * 3, y + b * 5, b * 2, b * 8);
        g.setColor(new Color(0x4C873B));
        g.fillRect(x, y + b * 3, b * 8, b * 6);
        g.setColor(new Color(0x5A9844));
        g.fillRect(x + b * 2, y, b * 6, b * 6);
        g.fillRect(x - b, y + b * 5, b * 5, b * 4);
    }

    private static void drawLamp(Graphics2D g, int x, int y, int b) {
        g.setColor(new Color(0x5E4631));
        g.fillRect(x, y, b, b * 9);
        g.setColor(new Color(0xFFD77A));
        g.fillRect(x - b, y, b * 3, b * 2);
        g.setColor(new Color(0xF4E3A2));
        g.fillRect(x, y + 2, b, b);
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
    private static Color withAlpha(int argb, int alpha) {
        return new Color((argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24), true);
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

}
