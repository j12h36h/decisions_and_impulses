package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Three-minute looping world-entry simulation used by DAI Engine 4.1.
 * The renderer is intentionally resource-independent and safe while no client
 * level exists. It models a universe collapsing toward the destination point,
 * then restarts only after 180 seconds if world generation is still active.
 */
public final class DAI_UniverseImplosionRenderer {

    public static final long LOOP_NANOS = 180_000_000_000L;
    private static final Map<Screen, Long> STARTS = new WeakHashMap<>();

    private DAI_UniverseImplosionRenderer() {}

    public static double phase(Screen screen) {
        long now = System.nanoTime();
        long start;
        synchronized (STARTS) {
            start = STARTS.computeIfAbsent(screen, ignored -> now);
        }
        long elapsed = Math.max(0L, now - start);
        return (elapsed % LOOP_NANOS) / (double) LOOP_NANOS;
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Screen screen,
            int width,
            int height,
            DAI_PresentationProfileService.Profile profile
    ) {
        if (graphics == null || screen == null || width <= 0 || height <= 0) return;
        DAI_PresentationProfileService.Profile p = profile == null
                ? DAI_PresentationProfileService.Profile.DEFAULT
                : profile;
        double t = phase(screen);
        double eased = smooth(t);
        int cx = width / 2;
        int cy = height / 2;

        graphics.fillGradient(0, 0, width, height, p.backgroundTop(), p.backgroundBottom());

        // 3D-ish star/fragments: their projected radius collapses over the
        // full 180-second timeline while their angular velocity accelerates.
        int fragments = Math.max(72, Math.min(220, (width * height) / 2600));
        double collapse = Math.max(0.018D, 1.0D - eased * 0.965D);
        double spin = t * t * 24.0D;
        for (int i = 0; i < fragments; i++) {
            long hash = mix(0xD41A9E3779B97F4AL + i * 0x9E3779B97F4A7C15L);
            double normalized = 0.12D + ((hash >>> 11) & 0xFFFFL) / 65535.0D * 0.88D;
            double angle = ((hash >>> 29) & 0xFFFFL) / 65535.0D * Math.PI * 2.0D
                    + spin * (0.22D + (i % 9) * 0.025D);
            double depth = 0.34D + ((hash >>> 45) & 0x1FFL) / 511.0D * 1.4D;
            double radius = Math.hypot(width, height) * 0.62D * normalized * collapse * depth;
            double squash = 0.46D + 0.30D * Math.sin(i * 1.83D + t * Math.PI * 2.0D);
            int x = cx + (int)(Math.cos(angle) * radius);
            int y = cy + (int)(Math.sin(angle) * radius * squash);
            if (x < -8 || y < -8 || x > width + 8 || y > height + 8) continue;

            double proximity = 1.0D - Math.min(1.0D, radius / Math.max(1.0D, Math.hypot(width, height) * 0.6D));
            int alpha = 40 + (int)(proximity * 185.0D);
            int base = i % 7 == 0 ? p.primary() : (i % 5 == 0 ? p.secondary() : p.text());
            int color = withAlpha(base, alpha);
            int size = 1 + (int)(proximity * 3.0D);
            graphics.fill(x, y, x + size, y + size, color);

            // Motion streak toward the singularity.
            if (i % 3 == 0) {
                int tx = x + (int)((cx - x) * (0.015D + proximity * 0.05D));
                int ty = y + (int)((cy - y) * (0.015D + proximity * 0.05D));
                drawLine(graphics, x, y, tx, ty, withAlpha(color, Math.max(20, alpha / 2)));
            }
        }

        // Collapsing universe shells.
        int maxR = Math.max(width, height);
        for (int ring = 0; ring < 8; ring++) {
            double ringPhase = (t * (0.35D + ring * 0.035D) + ring / 8.0D) % 1.0D;
            double rr = maxR * (0.58D * (1.0D - ringPhase)) * collapse + 8.0D;
            int alpha = (int)(100.0D * (1.0D - ringPhase));
            drawEllipse(graphics, cx, cy, (int)rr, Math.max(2, (int)(rr * 0.34D)),
                    withAlpha(ring % 2 == 0 ? p.primary() : p.secondary(), alpha));
        }

        // Destination singularity grows slowly rather than instantly, making
        // the animation visually meaningful for unusually long generation.
        int core = 5 + (int)(Math.pow(t, 1.6D) * Math.min(width, height) * 0.085D);
        for (int r = core + 14; r >= core; r -= 4) {
            int a = Math.max(12, 86 - (r - core) * 4);
            graphics.fill(cx - r, cy - 2, cx + r + 1, cy + 3, withAlpha(p.primary(), a));
            graphics.fill(cx - 2, cy - r, cx + 3, cy + r + 1, withAlpha(p.secondary(), a));
        }
        graphics.fill(cx - core, cy - core, cx + core + 1, cy + core + 1, withAlpha(0xFF000000, 0xD8));
        graphics.outline(cx - core - 2, cy - core - 2, core * 2 + 5, core * 2 + 5, withAlpha(p.primary(), 0xCC));

        // Loop timeline indicator: one revolution = exactly three minutes.
        int barWidth = Math.max(120, Math.min(360, width - 48));
        int bx = cx - barWidth / 2;
        int by = Math.max(12, height - 24);
        graphics.fill(bx, by, bx + barWidth, by + 2, 0x44000000);
        graphics.fill(bx, by, bx + (int)Math.round(barWidth * t), by + 2, withAlpha(p.primary(), 0xAA));
    }

    private static double smooth(double value) {
        double x = Math.max(0.0D, Math.min(1.0D, value));
        return x * x * (3.0D - 2.0D * x);
    }

    private static void drawEllipse(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        int lastX = cx + rx;
        int lastY = cy;
        for (int i = 1; i <= 56; i++) {
            double a = Math.PI * 2.0D * i / 56.0D;
            int x = cx + (int)(Math.cos(a) * rx);
            int y = cy + (int)(Math.sin(a) * ry);
            drawLine(g, lastX, lastY, x, y, color);
            lastX = x;
            lastY = y;
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

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ (value >>> 33);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
