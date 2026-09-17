package io.github.j12h36h.dai.client.branding;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationDefinition;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellPresentationRepository;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** DAI 4.1 fallback loading presentation driven by the selected presentation profile. */
public final class DAI_UniverseLoadingRenderer {
    private DAI_UniverseLoadingRenderer() {}

    public static void render(GuiGraphicsExtractor graphics, int width, int height, float progress) {
        renderInternal(graphics, width, height, progress, true);
    }

    /**
     * Bootstrap-safe DAI loader. This path intentionally ignores resource-pack
     * textures and uses only fills/lines plus the selected profile's colors.
     * It is safe while resources are reloading, registries are not yet bound,
     * and while Minecraft is creating/moving between worlds.
     */
    public static void renderSafe(GuiGraphicsExtractor graphics, int width, int height, float progress) {
        renderInternal(graphics, width, height, progress, false);
    }

    private static void renderInternal(
            GuiGraphicsExtractor graphics,
            int width,
            int height,
            float progress,
            boolean allowProfileTextures
    ) {
        if (graphics == null || width <= 0 || height <= 0) return;
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_ShellPresentationDefinition.SafeLoadingStyle safeStyle = allowProfileTextures
                ? null
                : DAI_ShellPresentationRepository.current().safeLoading();
        int backgroundTop = safeStyle != null && safeStyle.backgroundTop() != null ? safeStyle.backgroundTop() : profile.backgroundTop();
        int backgroundBottom = safeStyle != null && safeStyle.backgroundBottom() != null ? safeStyle.backgroundBottom() : profile.backgroundBottom();
        int primary = safeStyle != null && safeStyle.primary() != null ? safeStyle.primary() : profile.primary();
        int secondary = safeStyle != null && safeStyle.secondary() != null ? safeStyle.secondary() : profile.secondary();
        int text = safeStyle != null && safeStyle.text() != null ? safeStyle.text() : profile.text();
        boolean showPercentage = safeStyle == null || safeStyle.showPercentage();
        boolean determinate = progress >= 0.0F && Float.isFinite(progress);
        float p = determinate ? clamp(progress) : 0.0F;

        Identifier background = allowProfileTextures ? safeTexture(profile.backgroundTexture()) : null;
        if (background != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, background, 0, 0, 0.0F, 0.0F,
                    width, height, width, height, 0xFFFFFFFF);
        } else {
            graphics.fillGradient(0, 0, width, height, backgroundTop, backgroundBottom);
        }

        int cx = width / 2;
        int cy = height / 2;
        Identifier logo = allowProfileTextures ? safeTexture(profile.logoTexture()) : null;
        if (logo != null) {
            int size = Math.min(profile.logoSize(), Math.max(16, Math.min(width, height) / 2));
            graphics.blit(RenderPipelines.GUI_TEXTURED, logo, cx - size / 2, cy - size / 2 - 30,
                    0.0F, 0.0F, size, size, size, size, 0xFFFFFFFF);
        }

        if (profile.minimal()) {
            int barWidth = Math.max(80, Math.min(280, width - 48));
            int barY = cy + 28;
            graphics.fill(cx - barWidth / 2, barY, cx + barWidth / 2, barY + 3, 0x55333333);
            int filled = determinate ? Math.round(barWidth * p) : Math.floorMod((int) (System.nanoTime() / 8_000_000L), barWidth);
            if (determinate) {
                graphics.fill(cx - barWidth / 2, barY, cx - barWidth / 2 + filled, barY + 3, primary);
            } else {
                int start = cx - barWidth / 2 + Math.max(0, filled - 30);
                int end = Math.min(cx + barWidth / 2, start + 30);
                graphics.fill(start, barY, end, barY + 3, primary);
            }
            if (showPercentage) {
                drawProgress(graphics, cx, cy - 8, determinate ? Math.round(p * 100.0F) : -1, 3, text);
            }
            return;
        }

        double time = System.nanoTime() / 1_000_000_000.0D;
        int base = Math.max(42, Math.min(90, Math.min(width, height) / 4));
        int dimPrimary = withAlpha(primary, 0x66);
        int dimSecondary = withAlpha(secondary, 0x66);

        for (int i = 0; i < 20; i++) {
            double a = i * 2.399963229728653D + time * (i % 3 == 0 ? 0.03D : -0.018D);
            int r = base + 38 + (i * 17 % Math.max(24, base));
            int x = cx + (int) Math.round(Math.cos(a) * r * 1.7D);
            int y = cy + (int) Math.round(Math.sin(a) * r * 0.72D);
            if (x > 2 && x < width - 2 && y > 2 && y < height - 2) {
                graphics.fill(x, y, x + 1, y + 1, i % 2 == 0 ? dimPrimary : dimSecondary);
            }
        }

        ring(graphics, cx, cy, base + 18, (base + 18) * 0.44D, time * 0.34D,
                dimSecondary, secondary, -1.0F);
        ring(graphics, cx, cy, base, base * 0.63D, -time * 0.52D,
                dimPrimary, primary, determinate ? p : -1.0F);
        ring(graphics, cx, cy, Math.max(26, base - 24), Math.max(14, (base - 24) * 0.38D), time * 0.78D,
                withAlpha(secondary, 0x44), secondary, -1.0F);

        if (showPercentage) {
            drawProgress(graphics, cx, cy - 8, determinate ? Math.round(p * 100.0F) : -1,
                    Math.max(2, Math.min(4, height / 180)), text);
        }
        graphics.outline(cx - 18, cy - 18, 36, 36, withAlpha(secondary, 0x88));
        graphics.outline(cx - 15, cy - 15, 30, 30, withAlpha(primary, 0x88));
        graphics.fill(cx - 2, cy + 14, cx + 3, cy + 16, withAlpha(text, 0xDD));
    }

    private static void ring(GuiGraphicsExtractor g, int cx, int cy, double rx, double ry,
                             double rotation, int dim, int bright, float progress) {
        int segments = 84;
        for (int i = 0; i < segments; i++) {
            double a = rotation + (Math.PI * 2.0D * i / segments);
            int x = cx + (int) Math.round(Math.cos(a) * rx);
            int y = cy + (int) Math.round(Math.sin(a) * ry);
            boolean active = progress >= 0.0F && i <= Math.round(progress * (segments - 1));
            int size = (i % 7 == 0) ? 3 : 2;
            g.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, active ? bright : dim);
        }
    }

    private static void drawProgress(GuiGraphicsExtractor g, int cx, int top, int value, int scale, int color) {
        String text = value < 0 ? "--%" : Integer.toString(Math.max(0, Math.min(100, value))) + "%";
        int glyphW = 3 * scale, gap = scale;
        int total = text.length() * glyphW + (text.length() - 1) * gap;
        int x = cx - total / 2;
        for (int i = 0; i < text.length(); i++) {
            drawGlyph(g, x, top, text.charAt(i), scale, color);
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

    private static Identifier safeTexture(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty() || value.endsWith(":")) return null;
        return Identifier.tryParse(value);
    }

    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }
    private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
}
