package io.github.j12h36h.dai.client.branding;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Registry- and resource-independent vanilla-style village splash artwork.
 *
 * This renderer deliberately uses GUI fill primitives only. It is therefore
 * safe during Minecraft's earliest resource reload / component-binding phase,
 * before DAI can construct ItemStacks or resolve block models for the real
 * 3-D loading village. Once scene models are safe, callers render the 3-D
 * village underneath this artwork and fade the splash away.
 */
public final class DAI_VanillaVillageSplashRenderer {

    private DAI_VanillaVillageSplashRenderer() {}

    public static void render(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress
    ) {
        render(graphics, width, height, progress, 1.0F);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress,
            float opacity
    ) {
        if (graphics == null || width <= 0 || height <= 0) return;

        float alpha = clamp(opacity);
        if (alpha <= 0.001F) return;

        int horizon = Math.max(1, Math.round(height * 0.53F));
        int ground = Math.max(horizon + 1, Math.round(height * 0.69F));
        int block = Math.max(2, Math.min(width / 128, height / 80));

        // Warm vanilla overworld sky. Every color receives the requested alpha
        // so the same painting can crossfade cleanly over the live 3-D scene.
        graphics.fillGradient(
                0, 0, width, horizon,
                color(0xFF73B8E8, alpha),
                color(0xFFC8E7F5, alpha)
        );

        // Square sun and block-cloud silhouettes.
        int sun = Math.max(14, Math.min(width, height) / 11);
        graphics.fill(width - sun * 2, sun / 2, width - sun, sun + sun / 2,
                color(0xFFFFF0A0, alpha));
        cloud(graphics, Math.round(width * 0.10F), Math.round(height * 0.15F), block, alpha);
        cloud(graphics, Math.round(width * 0.55F), Math.round(height * 0.10F), Math.max(2, block - 1), alpha);

        // Distant layered hills keep the safe image visually related to the
        // later 3-D Minecraft scene without requiring any textures or models.
        hill(graphics, 0, horizon - block * 10, width / 3, block, 0xFF6C9A57, alpha);
        hill(graphics, width / 5, horizon - block * 13, width / 2, block, 0xFF76A65C, alpha);
        hill(graphics, width * 3 / 5, horizon - block * 9, width / 2, block, 0xFF668E50, alpha);

        graphics.fill(0, horizon, width, ground, color(0xFF6FA64F, alpha));
        graphics.fill(0, ground, width, height, color(0xFF5D8C42, alpha));

        // Perspective village road.
        int cx = width / 2;
        polygonPath(graphics, cx, horizon + block * 2, cx, height, width, block, alpha);

        // Layered village skyline. Multiple structures make this read as a
        // real settlement rather than two isolated icons on a flat field.
        house(graphics, Math.round(width * 0.07F), horizon - block * 10,
                block * 13, block * 11, block, 0xFFD8B675, 0xFF7E4D2B, alpha);
        house(graphics, Math.round(width * 0.25F), horizon - block * 13,
                block * 15, block * 13, block, 0xFFCFAB6C, 0xFF684026, alpha);
        house(graphics, Math.round(width * 0.66F), horizon - block * 12,
                block * 15, block * 12, block, 0xFFD1AC6C, 0xFF754426, alpha);
        house(graphics, Math.round(width * 0.82F), horizon - block * 9,
                block * 11, block * 10, block, 0xFFC7A263, 0xFF684026, alpha);
        tower(graphics, Math.round(width * 0.43F), horizon - block * 16, block, alpha);

        // Central well / bell-square silhouette.
        well(graphics, cx - block * 5, horizon - block, block, alpha);

        // Foreground farm and market details.
        farm(graphics, Math.round(width * 0.035F), ground + block,
                Math.max(block * 23, width / 5), block, alpha);
        market(graphics, Math.round(width * 0.72F), ground + block * 2, block, alpha);
        pond(graphics, Math.round(width * 0.60F), ground + block * 7, block, alpha);
        hayStack(graphics, Math.round(width * 0.84F), ground + block * 9, block, alpha);
        fenceLine(graphics, Math.round(width * 0.30F), ground + block * 8, block * 10, block, alpha);

        // Trees, lamps and small natural details frame the road.
        tree(graphics, Math.round(width * 0.87F), horizon - block * 10, block, alpha);
        tree(graphics, Math.round(width * 0.16F), horizon - block * 8, Math.max(2, block - 1), alpha);
        tree(graphics, Math.round(width * 0.58F), horizon - block * 7, Math.max(2, block - 2), alpha);
        lamp(graphics, Math.round(width * 0.37F), ground - block * 7, block, alpha);
        lamp(graphics, Math.round(width * 0.63F), ground - block * 7, block, alpha);
        groundDetails(graphics, width, height, ground, block, alpha);

        // Vignette/bottom readability layer.
        graphics.fillGradient(
                0, Math.max(0, height - Math.max(48, height / 4)), width, height,
                color(0x00000000, alpha),
                color(0xAA172016, alpha)
        );

        drawProgress(graphics, width, height, progress, alpha);
    }

    private static void cloud(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int c = color(0xEFFFFFFF, a);
        g.fill(x, y + b * 2, x + b * 13, y + b * 5, c);
        g.fill(x + b * 3, y, x + b * 8, y + b * 5, c);
        g.fill(x + b * 8, y + b, x + b * 11, y + b * 5, c);
    }

    private static void hill(GuiGraphicsExtractor g, int x, int y, int span, int b, int c, float a) {
        int color = color(c, a);
        int step = Math.max(2, b * 2);
        int blocks = Math.max(1, span / step);
        for (int i = 0; i < blocks; i++) {
            double normalized = blocks <= 1 ? 0.0D : i / (double)(blocks - 1);
            double arch = 1.0D - Math.abs(normalized * 2.0D - 1.0D);
            int top = y - (int)Math.round(arch * b * 7.0D);
            int left = x + i * step;
            g.fill(left, top, left + step + 1, Math.max(top + 1, y + b * 13), color);
        }
    }

    private static void house(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            int b,
            int wall,
            int roof,
            float a
    ) {
        int wallColor = color(wall, a);
        int wallShadow = color(0xFFB88F58, a);
        int roofColor = color(roof, a);
        int roofLight = color(0xFF94603A, a);
        int roofDark = color(0xFF54311E, a);
        int timber = color(0xFF5A3821, a);
        int foundation = color(0xFF77736A, a);
        int mortar = color(0xFF929087, a);
        int glass = color(0xFF9CD9E8, a);
        int glassGlow = color(0xFFE8E1A0, a);
        int shutter = color(0xFF6D4829, a);
        int door = color(0xFF744724, a);
        int flower = color(0xFFD85B5B, a);
        int planter = color(0xFF754A2A, a);

        int roofRows = 5;
        int wallTop = y + roofRows * b;
        int bottom = y + h;
        int cx = x + w / 2;

        // Stone foundation and slightly shaded plaster wall panels.
        g.fill(x, wallTop, x + w, bottom, wallColor);
        g.fill(x + b, wallTop + b, x + w / 2, bottom - b, wallShadow);
        g.fill(x, bottom - b, x + w, bottom, foundation);
        for (int px = x; px < x + w; px += Math.max(2, b * 2)) {
            g.fill(px, bottom - b, Math.min(x + w, px + b), bottom, mortar);
        }

        // Correct roof orientation: narrow ridge at the top, widening toward
        // the eaves. This is the inverse of the previous accidental funnel.
        int ridgeHalf = Math.max(b * 2, w / 9);
        int eaveHalf = w / 2 + b;
        for (int row = 0; row < roofRows; row++) {
            float t = row / (float)Math.max(1, roofRows - 1);
            int half = Math.round(ridgeHalf + (eaveHalf - ridgeHalf) * t);
            int ry = y + row * b;
            g.fill(cx - half, ry, cx + half, ry + b + 1, (row & 1) == 0 ? roofColor : roofLight);
        }
        g.fill(cx - eaveHalf, wallTop - 1, cx + eaveHalf, wallTop + Math.max(1, b / 2), roofDark);
        g.fill(cx - ridgeHalf, y, cx + ridgeHalf, y + Math.max(1, b / 2), roofDark);

        // Chimney, timber frame, center beam and corner posts.
        int chimneyX = x + Math.round(w * 0.70F);
        g.fill(chimneyX, y + b, chimneyX + b * 2, wallTop + b, foundation);
        g.fill(chimneyX + Math.max(1, b / 2), y + b, chimneyX + b * 2, y + b * 2, mortar);
        g.fill(x, wallTop, x + b, bottom, timber);
        g.fill(x + w - b, wallTop, x + w, bottom, timber);
        g.fill(cx - Math.max(1, b / 2), wallTop, cx + Math.max(1, b / 2), bottom, timber);
        int beamY = wallTop + Math.max(b * 2, (bottom - wallTop) / 2);
        g.fill(x, beamY, x + w, beamY + Math.max(1, b / 2), timber);

        // Framed windows + shutters + warm inner glints.
        int windowY = wallTop + b * 2;
        int windowW = Math.max(b * 3, w / 5);
        int windowH = Math.max(b * 2, (bottom - wallTop) / 4);
        window(g, x + b * 2, windowY, windowW, windowH, glass, glassGlow, shutter);
        window(g, x + w - b * 2 - windowW, windowY, windowW, windowH, glass, glassGlow, shutter);

        // Door with lintel/handle and small flower boxes.
        int doorW = Math.max(b * 2, w / 7);
        int doorX = cx - doorW / 2;
        int doorY = bottom - Math.max(b * 5, (bottom - wallTop) / 2);
        g.fill(doorX, doorY, doorX + doorW, bottom, door);
        g.fill(doorX - Math.max(1, b / 2), doorY - Math.max(1, b / 2),
                doorX + doorW + Math.max(1, b / 2), doorY, timber);
        g.fill(doorX + doorW - Math.max(2, b), doorY + Math.max(2, b * 2),
                doorX + doorW - Math.max(1, b / 2), doorY + Math.max(3, b * 2 + 1), glassGlow);

        int boxY = windowY + windowH + Math.max(1, b / 2);
        g.fill(x + b * 2, boxY, x + b * 2 + windowW, boxY + Math.max(1, b / 2), planter);
        g.fill(x + w - b * 2 - windowW, boxY, x + w - b * 2, boxY + Math.max(1, b / 2), planter);
        for (int i = 0; i < 3; i++) {
            int flowerW = Math.max(1, b / 2);
            g.fill(x + b * 2 + b + i * b, boxY - flowerW,
                    x + b * 2 + b + i * b + flowerW, boxY, flower);
            g.fill(x + w - b * 2 - windowW + b + i * b, boxY - flowerW,
                    x + w - b * 2 - windowW + b + i * b + flowerW, boxY, flower);
        }
    }

    private static void window(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            int glass,
            int glow,
            int shutter
    ) {
        int frame = Math.max(1, Math.min(w, h) / 6);
        g.fill(x - frame, y, x, y + h, shutter);
        g.fill(x + w, y, x + w + frame, y + h, shutter);
        g.fill(x, y, x + w, y + h, glass);
        g.fill(x + w / 2, y, x + w / 2 + 1, y + h, shutter);
        g.fill(x, y + h / 2, x + w, y + h / 2 + 1, shutter);
        g.fill(x + 1, y + 1, x + Math.max(2, w / 3), y + Math.max(2, h / 3), glow);
    }

    private static void tower(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int wall = color(0xFFB7A06D, a);
        int wallShadow = color(0xFFA68E5F, a);
        int timber = color(0xFF62432B, a);
        int roof = color(0xFF6B4226, a);
        int roofLight = color(0xFF835334, a);
        int roofDark = color(0xFF4D2F1D, a);
        int glass = color(0xFF8DCEE1, a);
        int bell = color(0xFFD8A83D, a);
        int stone = color(0xFF7E7B72, a);

        int w = b * 11;
        int roofRows = 6;
        int wallTop = y + roofRows * b;
        int bottom = y + b * 22;
        int cx = x + w / 2;

        g.fill(x, wallTop, x + w, bottom, wall);
        g.fill(x + b, wallTop + b, cx, bottom - b, wallShadow);
        g.fill(x, bottom - b, x + w, bottom, stone);
        g.fill(x, wallTop, x + b, bottom, timber);
        g.fill(x + w - b, wallTop, x + w, bottom, timber);
        g.fill(cx, wallTop, cx + Math.max(1, b / 2), bottom, timber);
        g.fill(x, wallTop + b * 6, x + w, wallTop + b * 6 + Math.max(1, b / 2), timber);

        int ridgeHalf = b * 2;
        int eaveHalf = w / 2 + b * 2;
        for (int row = 0; row < roofRows; row++) {
            float t = row / (float)Math.max(1, roofRows - 1);
            int half = Math.round(ridgeHalf + (eaveHalf - ridgeHalf) * t);
            int ry = y + row * b;
            g.fill(cx - half, ry, cx + half, ry + b + 1, (row & 1) == 0 ? roof : roofLight);
        }
        g.fill(cx - eaveHalf, wallTop - 1, cx + eaveHalf, wallTop + Math.max(1, b / 2), roofDark);

        // Bell opening, glass below it, and door at the base.
        int openingY = wallTop + b * 2;
        g.fill(x + b * 3, openingY, x + b * 8, openingY + b * 4, roofDark);
        g.fill(cx - b, openingY + b, cx + b, openingY + b * 3, bell);
        g.fill(x + b * 4, wallTop + b * 8, x + b * 7, wallTop + b * 12, glass);
        g.fill(x + b * 5, bottom - b * 5, x + b * 7, bottom, timber);
    }

    private static void market(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int post = color(0xFF62452F, a);
        int clothA = color(0xFFB84A3C, a);
        int clothB = color(0xFFF2DDC1, a);
        int crate = color(0xFF9A6938, a);
        g.fill(x, y, x + b, y + b * 9, post);
        g.fill(x + b * 12, y, x + b * 13, y + b * 9, post);
        for (int i = 0; i < 6; i++) {
            g.fill(x + i * b * 2, y, x + (i + 1) * b * 2, y + b * 3,
                    (i & 1) == 0 ? clothA : clothB);
        }
        g.fill(x + b * 2, y + b * 6, x + b * 6, y + b * 9, crate);
        g.fill(x + b * 8, y + b * 6, x + b * 12, y + b * 9, crate);
    }

    private static void groundDetails(
            GuiGraphicsExtractor g,
            int width,
            int height,
            int ground,
            int b,
            float a
    ) {
        int grass = color(0xFF4F873A, a);
        int flower = color(0xFFE7D64E, a);
        int flowerRed = color(0xFFD85B5B, a);
        int flowerBlue = color(0xFF6A8DD8, a);
        int stone = color(0xFF77766F, a);
        for (int i = 0; i < 96; i++) {
            int x = Math.floorMod(i * 157 + 31, Math.max(1, width));
            int y = ground + Math.floorMod(i * 71 + 13, Math.max(1, height - ground - 18));
            if (x > width * 0.33F && x < width * 0.67F) continue;
            int c = (i % 17 == 0) ? flowerRed
                    : ((i % 13 == 0) ? flowerBlue
                    : ((i % 9 == 0) ? flower
                    : ((i % 7 == 0) ? stone : grass)));
            int w = Math.max(1, (i % 4 == 0) ? b : b / 2);
            int h = Math.max(1, (i % 3 == 0) ? b : b / 2);
            g.fill(x, y, x + w, y + h, c);
        }
    }
    private static void pond(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int sand = color(0xFFD7C48A, a);
        int water = color(0xFF4E9BCC, a);
        int waterLight = color(0xFF75B9DB, a);
        int reed = color(0xFF6F9A42, a);
        g.fill(x, y, x + b * 15, y + b * 6, sand);
        g.fill(x + b, y + b, x + b * 14, y + b * 5, water);
        g.fill(x + b * 3, y + b, x + b * 10, y + b * 2, waterLight);
        for (int i = 0; i < 5; i++) {
            int rx = x + b * (2 + i * 3);
            g.fill(rx, y - b * (i % 2 + 1), rx + Math.max(1, b / 2), y + b, reed);
        }
    }

    private static void hayStack(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int hay = color(0xFFD5A83A, a);
        int band = color(0xFF9B6D29, a);
        for (int row = 0; row < 3; row++) {
            int count = 3 - row;
            int offset = row * b * 2;
            for (int i = 0; i < count; i++) {
                int bx = x + offset + i * b * 4;
                int by = y - row * b * 3;
                g.fill(bx, by, bx + b * 4, by + b * 3, hay);
                g.fill(bx, by + b, bx + b * 4, by + b + Math.max(1, b / 2), band);
            }
        }
    }

    private static void fenceLine(GuiGraphicsExtractor g, int x, int y, int width, int b, float a) {
        int fence = color(0xFF91623A, a);
        g.fill(x, y, x + width, y + Math.max(1, b / 2), fence);
        g.fill(x, y + b * 2, x + width, y + b * 2 + Math.max(1, b / 2), fence);
        for (int px = x; px <= x + width; px += Math.max(b * 4, 4)) {
            g.fill(px, y - b, px + b, y + b * 4, fence);
        }
    }

    private static void well(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int stone = color(0xFF8B8B84, a);
        int dark = color(0xFF676761, a);
        int water = color(0xFF3E8FD1, a);
        int roof = color(0xFF765034, a);
        g.fill(x, y + b * 6, x + b * 10, y + b * 9, stone);
        g.fill(x + b, y + b * 7, x + b * 9, y + b * 8, water);
        g.fill(x + b, y + b, x + b * 2, y + b * 7, dark);
        g.fill(x + b * 8, y + b, x + b * 9, y + b * 7, dark);
        g.fill(x, y, x + b * 10, y + b * 2, roof);
    }

    private static void farm(GuiGraphicsExtractor g, int x, int y, int w, int b, float a) {
        int soil = color(0xFF6E4A2C, a);
        int wet = color(0xFF3E6C83, a);
        int crop = color(0xFFE7C84A, a);
        int cropDark = color(0xFF9CB540, a);
        int fence = color(0xFF9A6A3B, a);
        int rows = 5;
        int rowH = Math.max(2, b * 2);
        g.fill(x, y, x + w, y + rows * rowH + b * 2, soil);
        for (int r = 0; r < rows; r++) {
            int ry = y + r * rowH;
            if (r == 2) {
                g.fill(x, ry, x + w, ry + Math.max(1, b), wet);
                continue;
            }
            for (int px = x + b; px < x + w - b; px += b * 3) {
                g.fill(px, ry - b, px + b, ry + b, r % 2 == 0 ? crop : cropDark);
            }
        }
        g.fill(x, y - b, x + w, y, fence);
        g.fill(x, y + rows * rowH + b * 2, x + w, y + rows * rowH + b * 3, fence);
        for (int px = x; px <= x + w; px += Math.max(b * 5, 5)) {
            g.fill(px, y - b * 2, px + b, y + rows * rowH + b * 4, fence);
        }
    }

    private static void tree(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int trunk = color(0xFF6A4527, a);
        int leaves = color(0xFF4F8B3D, a);
        int leavesLight = color(0xFF5C9D47, a);
        g.fill(x + b * 4, y + b * 5, x + b * 6, y + b * 13, trunk);
        g.fill(x + b, y + b * 2, x + b * 9, y + b * 8, leaves);
        g.fill(x + b * 3, y, x + b * 8, y + b * 5, leavesLight);
        g.fill(x, y + b * 4, x + b * 5, y + b * 8, leavesLight);
    }

    private static void lamp(GuiGraphicsExtractor g, int x, int y, int b, float a) {
        int post = color(0xFF5C4430, a);
        int glow = color(0xFFFFD978, a);
        g.fill(x, y, x + b, y + b * 7, post);
        g.fill(x - b, y, x + b * 2, y + b * 2, glow);
    }

    private static void polygonPath(GuiGraphicsExtractor g, int cxTop, int yTop, int cxBottom, int yBottom, int width, int b, float a) {
        int path = color(0xFFB8A16E, a);
        int shade = color(0xFFA79062, a);
        int rows = Math.max(1, (yBottom - yTop) / Math.max(1, b));
        for (int i = 0; i < rows; i++) {
            float t = i / (float)Math.max(1, rows - 1);
            int half = Math.round((b * 2) + t * Math.max(b * 14, width * 0.125F));
            int center = Math.round(cxTop + (cxBottom - cxTop) * t);
            int y = yTop + i * b;
            g.fill(center - half, y, center + half, y + b + 1, i % 3 == 0 ? shade : path);
        }
    }

    private static void drawProgress(GuiGraphicsExtractor g, int width, int height, float progress, float a) {
        int barWidth = Math.max(96, Math.min(360, width - 48));
        int barHeight = Math.max(3, height / 180);
        int x = width / 2 - barWidth / 2;
        int y = height - Math.max(18, height / 14);
        g.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, color(0xAA111611, a));
        g.fill(x, y, x + barWidth, y + barHeight, color(0x886C705E, a));

        if (progress >= 0.0F && Float.isFinite(progress)) {
            int fill = Math.round(barWidth * clamp(progress));
            if (fill > 0) g.fill(x, y, x + fill, y + barHeight, color(0xFFE8E2C3, a));
        } else {
            int segment = Math.max(18, barWidth / 6);
            int travel = Math.max(1, barWidth - segment);
            int phase = Math.floorMod((int)(System.nanoTime() / 8_000_000L), travel + 1);
            g.fill(x + phase, y, x + phase + segment, y + barHeight, color(0xFFE8E2C3, a));
        }
    }

    private static int color(int argb, float opacity) {
        int sourceAlpha = (argb >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * clamp(opacity))));
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
