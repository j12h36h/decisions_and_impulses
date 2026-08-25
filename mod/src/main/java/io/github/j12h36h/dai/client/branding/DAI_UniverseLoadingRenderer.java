package io.github.j12h36h.dai.client.branding;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Lightweight texture-free DAI universe-ring loading presentation. */
public final class DAI_UniverseLoadingRenderer {
    private static final int BG_TOP = 0xFF08050D;
    private static final int BG_BOTTOM = 0xFF12091A;
    private static final int ORANGE = 0xFFFF8428;
    private static final int PURPLE = 0xFFA855F7;
    private static final int DIM_ORANGE = 0x66FF8428;
    private static final int DIM_PURPLE = 0x66A855F7;

    private DAI_UniverseLoadingRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height, float progress) {
        if (graphics == null || width <= 0 || height <= 0) return;
        boolean determinate = progress >= 0.0F && Float.isFinite(progress);
        float p = determinate ? clamp(progress) : 0.0F;
        graphics.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);

        int cx = width / 2;
        int cy = height / 2;
        double time = System.nanoTime() / 1_000_000_000.0D;
        int base = Math.max(42, Math.min(90, Math.min(width, height) / 4));

        // Sparse universe nodes keep the background alive without particles/shaders.
        for (int i = 0; i < 20; i++) {
            double a = i * 2.399963229728653D + time * (i % 3 == 0 ? 0.03D : -0.018D);
            int r = base + 38 + (i * 17 % Math.max(24, base));
            int x = cx + (int) Math.round(Math.cos(a) * r * 1.7D);
            int y = cy + (int) Math.round(Math.sin(a) * r * 0.72D);
            if (x > 2 && x < width - 2 && y > 2 && y < height - 2) graphics.fill(x, y, x + 1, y + 1, i % 2 == 0 ? DIM_ORANGE : DIM_PURPLE);
        }

        ring(graphics, cx, cy, base + 18, (base + 18) * 0.44D, time * 0.34D, DIM_PURPLE, PURPLE, -1.0F);
        ring(graphics, cx, cy, base, base * 0.63D, -time * 0.52D, DIM_ORANGE, ORANGE, determinate ? p : -1.0F);
        ring(graphics, cx, cy, Math.max(26, base - 24), Math.max(14, (base - 24) * 0.38D), time * 0.78D, 0x449F64D4, PURPLE, -1.0F);

        int scale = Math.max(2, Math.min(4, height / 180));
        if (determinate) drawPercent(graphics, cx, cy - 8, Math.round(p * 100.0F), scale);
        else drawUnknown(graphics, cx, cy - 8, scale);

        // Center node / index-universe core.
        graphics.outline(cx - 18, cy - 18, 36, 36, 0x88A855F7);
        graphics.outline(cx - 15, cy - 15, 30, 30, 0x88FF8428);
        graphics.fill(cx - 2, cy + 14, cx + 3, cy + 16, 0xDDFFFFFF);
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, double rx, double ry,
                             double rotation, int dim, int bright, float progress) {
        int segments = 84;
        for (int i = 0; i < segments; i++) {
            double a = rotation + (Math.PI * 2.0D * i / segments);
            int x = cx + (int) Math.round(Math.cos(a) * rx);
            int y = cy + (int) Math.round(Math.sin(a) * ry);
            boolean active = progress >= 0.0F && i <= Math.round(progress * (segments - 1));
            int color = active ? bright : dim;
            int size = (i % 7 == 0) ? 3 : 2;
            g.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, color);
        }
    }


    private static void drawUnknown(GuiGraphicsExtractor g, int cx, int top, int scale) {
        String text = "--%";
        int glyphW = 3 * scale, gap = scale;
        int total = text.length() * glyphW + (text.length() - 1) * gap;
        int x = cx - total / 2;
        for (int i = 0; i < text.length(); i++) {
            drawGlyph(g, x, top, text.charAt(i), scale, 0xFFF7F0FF);
            x += glyphW + gap;
        }
    }

    private static void drawPercent(GuiGraphicsExtractor g, int cx, int top, int value, int scale) {
        String text = Integer.toString(Math.max(0, Math.min(100, value))) + "%";
        int glyphW = 3 * scale;
        int gap = scale;
        int total = text.length() * glyphW + (text.length() - 1) * gap;
        int x = cx - total / 2;
        for (int i = 0; i < text.length(); i++) {
            drawGlyph(g, x, top, text.charAt(i), scale, 0xFFF7F0FF);
            x += glyphW + gap;
        }
    }

    private static void drawGlyph(GuiGraphicsExtractor g, int x, int y, char c, int s, int color) {
        String[] rows = switch (c) {
            case '0' -> new String[]{"111","101","101","101","111"};
            case '1' -> new String[]{"010","110","010","010","111"};
            case '2' -> new String[]{"111","001","111","100","111"};
            case '3' -> new String[]{"111","001","111","001","111"};
            case '4' -> new String[]{"101","101","111","001","001"};
            case '5' -> new String[]{"111","100","111","001","111"};
            case '6' -> new String[]{"111","100","111","101","111"};
            case '7' -> new String[]{"111","001","010","010","010"};
            case '8' -> new String[]{"111","101","111","101","111"};
            case '9' -> new String[]{"111","101","111","001","111"};
            case '%' -> new String[]{"101","001","010","100","101"};
            case '-' -> new String[]{"000","000","111","000","000"};
            default -> new String[]{"000","000","000","000","000"};
        };
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < 3; col++) {
                if (rows[row].charAt(col) == '1') g.fill(x + col*s, y + row*s, x + (col+1)*s, y + (row+1)*s, color);
            }
        }
    }

    private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
}
