package io.github.j12h36h.dai.client.branding;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Resource-independent DAI 4.3 intro/loading artwork.
 *
 * Only GUI primitives are used, so this can render before textures, models,
 * registries or the shell world are safe. The artwork deliberately belongs to
 * DAI rather than simulating Minecraft gameplay; gameplay presentation begins
 * only after the selected world has attached.
 */
public final class DAI_IntroSplashRenderer {

    private DAI_IntroSplashRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height, float progress) {
        if (graphics == null || width <= 0 || height <= 0) return;
        float p = clamp(progress);

        // Deep-space / engine-space field.
        graphics.fillGradient(0, 0, width, height, 0xFF080B16, 0xFF160A20);
        int cx = width / 2;
        int cy = height / 2 - Math.max(8, height / 22);
        int unit = Math.max(2, Math.min(width, height) / 120);

        // Horizon glow bands.
        int glowH = Math.max(16, height / 8);
        graphics.fillGradient(0, cy - glowH, width, cy + glowH, 0x001B76A8, 0x331B76A8);
        graphics.fillGradient(0, cy, width, cy + glowH * 2, 0x222C1747, 0x00100A1A);

        // Perspective network lanes leading toward the central DAI node.
        int lane = Math.max(1, unit / 2);
        for (int i = -5; i <= 5; i++) {
            int bottomX = cx + i * Math.max(18, width / 12);
            drawLine(graphics, bottomX, height, cx + i * unit * 2, cy + unit * 8, lane, 0x443B6C98);
        }
        for (int row = 0; row < 7; row++) {
            float t = row / 6.0F;
            int y = cy + unit * 9 + Math.round((height - (cy + unit * 9)) * t * t);
            int margin = Math.round(width * (0.46F - 0.34F * t));
            graphics.fill(margin, y, width - margin, y + 1, 0x333B6C98);
        }

        // Orbit/network rings: square-safe approximations made from line segments.
        int r1x = Math.max(unit * 18, Math.min(width / 5, 130));
        int r1y = Math.max(unit * 8, Math.min(height / 10, 58));
        diamond(graphics, cx, cy, r1x, r1y, Math.max(1, unit / 2), 0x886A54B8);
        diamond(graphics, cx, cy, r1x + unit * 8, r1y + unit * 5, Math.max(1, unit / 2), 0x55426D9A);

        // Satellite content nodes.
        node(graphics, cx - r1x, cy, unit, 0xFF56C6D8);
        node(graphics, cx + r1x, cy, unit, 0xFFFFA33C);
        node(graphics, cx, cy - r1y, unit, 0xFFA855F7);
        node(graphics, cx, cy + r1y, unit, 0xFF65D69B);

        // Central DAI engine mark. Block-letter geometry remains safe even
        // before the font atlas is usable.
        int markW = unit * 34;
        int markH = unit * 16;
        graphics.fill(cx - markW / 2 - unit * 3, cy - markH / 2 - unit * 3,
                cx + markW / 2 + unit * 3, cy + markH / 2 + unit * 3, 0x44110C22);
        graphics.fill(cx - markW / 2 - unit, cy - markH / 2 - unit,
                cx + markW / 2 + unit, cy + markH / 2 + unit, 0xAA241536);
        drawDAI(graphics, cx, cy, unit, 0xFFF4F7FF);

        // Minimal progress rail. It is deliberately presentation-only: the
        // shell readiness gate, not this estimate, decides when the splash ends.
        int railW = Math.max(120, Math.min(420, width * 3 / 5));
        int railH = Math.max(3, unit * 2);
        int railX = cx - railW / 2;
        int railY = Math.min(height - railH - unit * 8, cy + r1y + unit * 16);
        graphics.fill(railX, railY, railX + railW, railY + railH, 0x663D465B);
        int filled = Math.round((railW - 2) * p);
        if (filled > 0) graphics.fill(railX + 1, railY + 1, railX + 1 + filled, railY + railH - 1, 0xFFE1B35C);

        // Bottom vignette keeps the transition calm on wide aspect ratios.
        graphics.fillGradient(0, Math.max(0, height - height / 4), width, height, 0x00100818, 0xAA07060D);
    }

    private static void drawDAI(GuiGraphicsExtractor g, int cx, int cy, int u, int color) {
        int stroke = Math.max(2, u * 2);
        int h = u * 12;
        int top = cy - h / 2;
        int bottom = cy + h / 2;
        int x = cx - u * 14;

        // D
        g.fill(x, top, x + stroke, bottom, color);
        g.fill(x, top, x + u * 7, top + stroke, color);
        g.fill(x, bottom - stroke, x + u * 7, bottom, color);
        g.fill(x + u * 6, top + stroke, x + u * 8, bottom - stroke, color);

        // A
        x = cx - u * 2;
        g.fill(x, top + stroke, x + stroke, bottom, color);
        g.fill(x + u * 6, top + stroke, x + u * 6 + stroke, bottom, color);
        g.fill(x + stroke, top, x + u * 6, top + stroke, color);
        g.fill(x + stroke, cy - stroke / 2, x + u * 6, cy + stroke / 2 + 1, color);

        // I
        x = cx + u * 10;
        g.fill(x, top, x + u * 7, top + stroke, color);
        g.fill(x + u * 3, top, x + u * 3 + stroke, bottom, color);
        g.fill(x, bottom - stroke, x + u * 7, bottom, color);
    }

    private static void node(GuiGraphicsExtractor g, int cx, int cy, int u, int color) {
        int r = Math.max(4, u * 3);
        g.fill(cx - r - u, cy - r - u, cx + r + u, cy + r + u, 0x55120E22);
        g.fill(cx - r, cy - r, cx + r, cy + r, color);
        int inner = Math.max(1, r / 2);
        g.fill(cx - inner, cy - inner, cx + inner, cy + inner, 0xFF111725);
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy, int rx, int ry, int thickness, int color) {
        drawLine(g, cx, cy - ry, cx + rx, cy, thickness, color);
        drawLine(g, cx + rx, cy, cx, cy + ry, thickness, color);
        drawLine(g, cx, cy + ry, cx - rx, cy, thickness, color);
        drawLine(g, cx - rx, cy, cx, cy - ry, thickness, color);
    }

    private static void drawLine(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int thickness, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            g.fill(x0, y0, x0 + thickness, y0 + thickness, color);
            return;
        }
        for (int i = 0; i <= steps; i += Math.max(1, thickness)) {
            float t = i / (float) steps;
            int x = Math.round(x0 + dx * t);
            int y = Math.round(y0 + dy * t);
            g.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
