package io.github.j12h36h.dai.client.presentation;

import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderSafety;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Default DAI shell backdrop. Once Minecraft registry-backed models are safe,
 * the built-in presentation becomes a slowly orbiting vanilla-style Skyblock
 * landscape authored through the same generic scene system available to packs.
 * During bootstrap, or when that scene is unavailable, the original primitive
 * universe renderer remains as the resource-independent safety fallback.
 */
public final class DAI_UniverseShellRenderer {

    private static final String DEFAULT_LANDSCAPE_SCENE =
            "decisions_and_impulses:dai_universe_landscape";

    private DAI_UniverseShellRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height) {
        render(graphics, width, height, DAI_PresentationProfileService.selected(), System.nanoTime());
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            DAI_PresentationProfileService.Profile profile,
            long nowNanos
    ) {
        if (graphics == null || width <= 0 || height <= 0) return;
        DAI_PresentationProfileService.Profile p = profile == null
                ? DAI_PresentationProfileService.Profile.DEFAULT
                : profile;

        /*
         * The default DAI experience is itself data-driven. After the first
         * registry-safe client world has existed, every shell screen that uses
         * this renderer inherits the same 3-D Minecraft landscape. Custom
         * presentations are not forced through this scene; datapacks/resource
         * packs remain free to replace their own screens and environments.
         */
        if ("dai:default".equalsIgnoreCase(p.id())
                && DAI_SceneRenderSafety.registryModelsReady()
                && DAI_SceneRenderer.render(
                        graphics,
                        DEFAULT_LANDSCAPE_SCENE,
                        0, 0, width, height, 0.0F,
                        Map.of("shell.profile", p.id())
                )) {
            return;
        }

        graphics.fillGradient(0, 0, width, height, p.backgroundTop(), p.backgroundBottom());
        if (p.minimal()) return;

        double seconds = nowNanos / 1_000_000_000.0D;
        int horizon = Math.max(1, (int)(height * 0.70D));

        // Deep-space star layers. Positions are deterministic; only shimmer moves.
        int starCount = Math.max(38, Math.min(128, (width * height) / 4200));
        for (int i = 0; i < starCount; i++) {
            long hash = mix(i * 0x9E3779B97F4A7C15L + width * 31L + height * 17L);
            int sx = floorMod((int)(hash >>> 8), Math.max(1, width));
            int sy = floorMod((int)(hash >>> 28), Math.max(1, horizon));
            double pulse = 0.5D + 0.5D * Math.sin(seconds * (0.55D + (i % 7) * 0.07D) + i * 1.37D);
            int alpha = 45 + (int)(pulse * 130.0D);
            int rgb = (i % 9 == 0 ? p.secondary() : p.text()) & 0x00FFFFFF;
            int color = (alpha << 24) | rgb;
            int size = i % 19 == 0 ? 2 : 1;
            graphics.fill(sx, sy, sx + size, sy + size, color);
        }

        // Distant orbit/planet silhouette.
        int planetX = (int)(width * 0.76D + Math.sin(seconds * 0.045D) * width * 0.018D);
        int planetY = (int)(height * 0.27D + Math.cos(seconds * 0.04D) * height * 0.012D);
        int r = Math.max(20, Math.min(width, height) / 9);
        drawOrb(graphics, planetX, planetY, r, withAlpha(p.secondary(), 0x20), withAlpha(p.secondary(), 0x75));
        drawOrbit(graphics, planetX, planetY, r + 17, withAlpha(p.primary(), 0x55), seconds);

        // Layered landscape gives the title/settings/world hub a continuous world-space horizon.
        int far = horizon - Math.max(10, height / 18);
        for (int x = 0; x < width; x += 8) {
            double n = Math.sin(x * 0.024D + seconds * 0.015D) * 7.0D
                    + Math.sin(x * 0.009D + 1.7D) * 12.0D;
            int top = far - (int)n;
            graphics.fill(x, top, Math.min(width, x + 9), horizon + 10, withAlpha(p.secondary(), 0x18));
        }

        int near = horizon + Math.max(6, height / 30);
        for (int x = 0; x < width; x += 7) {
            double n = Math.sin(x * 0.019D - seconds * 0.02D) * 10.0D
                    + Math.sin(x * 0.041D + 0.4D) * 5.0D;
            int top = near - (int)n;
            graphics.fill(x, top, Math.min(width, x + 8), height, withAlpha(p.primary(), 0x12));
        }

        // Perspective energy lanes toward the center horizon.
        int center = width / 2;
        int laneColor = withAlpha(p.primary(), 0x28);
        for (int i = -6; i <= 6; i++) {
            int bottomX = center + i * Math.max(16, width / 13);
            drawLine(graphics, center, horizon, bottomX, height, laneColor);
        }
        for (int row = 0; row < 8; row++) {
            double f = row / 8.0D;
            f *= f;
            int yy = horizon + (int)((height - horizon) * f);
            graphics.fill(0, yy, width, yy + 1, withAlpha(p.secondary(), 0x18 + row * 3));
        }

        // Top/bottom cinematic falloff; deliberately not Minecraft's menu dirt/panorama language.
        graphics.fillGradient(0, 0, width, Math.max(20, height / 5), 0x9A000000, 0x00000000);
        graphics.fillGradient(0, Math.max(0, height - height / 4), width, height, 0x00000000, 0xB8000000);
    }

    public static void drawConnection(
            GuiGraphicsExtractor graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        drawLine(graphics, x0, y0, x1, y1, color);
    }

    private static void drawOrb(GuiGraphicsExtractor g, int cx, int cy, int r, int fill, int edge) {
        // Scanline circle avoids any dependency on custom textures/models.
        for (int y = -r; y <= r; y += 2) {
            int half = (int)Math.sqrt(Math.max(0.0D, r * (double)r - y * (double)y));
            g.fill(cx - half, cy + y, cx + half + 1, cy + y + 2, fill);
        }
        drawOrbit(g, cx, cy, r, edge, 0.0D);
    }

    private static void drawOrbit(GuiGraphicsExtractor g, int cx, int cy, int r, int color, double phase) {
        int previousX = cx + r;
        int previousY = cy;
        for (int i = 1; i <= 40; i++) {
            double a = phase * 0.07D + Math.PI * 2.0D * i / 40.0D;
            int x = cx + (int)(Math.cos(a) * r);
            int y = cy + (int)(Math.sin(a) * r * 0.38D);
            drawLine(g, previousX, previousY, x, y, color);
            previousX = x;
            previousY = y;
        }
    }

    private static void drawLine(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x += sx; }
            if (twice <= dx) { error += dx; y += sy; }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static int floorMod(int value, int mod) {
        if (mod <= 0) return 0;
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }
}
