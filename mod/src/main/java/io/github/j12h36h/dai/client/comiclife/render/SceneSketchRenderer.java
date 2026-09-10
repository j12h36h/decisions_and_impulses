package io.github.j12h36h.dai.client.comiclife.render;

import io.github.j12h36h.dai.comiclife.model.ComicLifePanel;
import io.github.j12h36h.dai.comiclife.story.ComicCompiler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Draws deterministic comic art from captured scene context; no texture pack is required. */
public final class SceneSketchRenderer {
    private SceneSketchRenderer() {}

    public static void render(GuiGraphicsExtractor g, Font font, ComicLifePanel p, int x, int y, int w, int h, int panelNumber) {
        int artH = Math.max(56, (int)(h * 0.58));
        int sky = skyColor(p.timeOfDay, p.dimension);
        int ground = groundColor(p.biome, p.dimension);
        int ink = 0xFF161318;
        int paper = 0xFFF2E6C8;

        g.fill(x, y, x + w, y + h, paper);
        g.outline(x, y, w, h, ink);
        g.fill(x + 2, y + 2, x + w - 2, y + artH, sky);
        g.fill(x + 2, y + artH - 18, x + w - 2, y + artH, ground);

        drawTerrain(g, p, x + 2, y + 2, w - 4, artH - 2, ink);
        drawWeather(g, p.weather, x + 2, y + 2, w - 4, artH - 20);
        drawEventSymbol(g, p, x + w / 2, y + artH - 29, ink);

        g.fill(x + 5, y + 5, x + 23, y + 17, 0xD0161318);
        g.text(font, Component.literal(Integer.toString(panelNumber)), x + 10, y + 7, 0xFFF8EDCF);

        String caption = p.caption == null || p.caption.isBlank() ? ComicCompiler.friendly(p.eventType) : p.caption;
        g.text(font, Component.literal(crop(caption, 52)), x + 7, y + artH + 5, ink);
        drawWrapped(g, font, p.narration, x + 7, y + artH + 18, w - 14, 3, 0xFF2A2225);

        int barX = x + 7;
        int barY = y + h - 10;
        int barW = Math.max(20, w - 14);
        float ratio = p.maxHealth <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, p.health / p.maxHealth));
        g.fill(barX, barY, barX + barW, barY + 4, 0x552A2225);
        g.fill(barX, barY, barX + Math.round(barW * ratio), barY + 4, healthColor(ratio));
    }

    private static void drawTerrain(GuiGraphicsExtractor g, ComicLifePanel p, int x, int y, int w, int h, int ink) {
        String biome = safe(p.biome);
        String dimension = safe(p.dimension);
        int horizon = y + h - 18;

        if (biome.contains("forest") || biome.contains("taiga") || biome.contains("jungle") || biome.contains("grove") || biome.contains("mangrove")) {
            for (int i = 0; i < 4; i++) {
                int tx = x + 12 + i * Math.max(18, (w - 30) / 4);
                int trunkH = 17 + (i % 2) * 8;
                g.fill(tx, horizon - trunkH, tx + 5, horizon + 2, 0xFF5C3A24);
                g.fill(tx - 8, horizon - trunkH - 13, tx + 13, horizon - trunkH + 2, foliageColor(biome));
                g.outline(tx - 8, horizon - trunkH - 13, 21, 15, ink);
            }
        }

        if (biome.contains("ocean") || biome.contains("river") || biome.contains("swamp") || biome.contains("beach")) {
            g.fill(x, horizon + 2, x + w, y + h, 0xFF315D77);
            for (int i = 0; i < 4; i++) {
                int yy = horizon + 5 + i * 4;
                g.fill(x + (i % 2) * 9, yy, x + w - 6, yy + 1, 0x66E8F4FF);
            }
        }

        if (dimension.contains("nether")) {
            g.fill(x + 7, horizon - 18, x + 18, horizon + 4, 0xFF4C1E1E);
            g.fill(x + w - 25, horizon - 31, x + w - 12, horizon + 4, 0xFF682328);
            g.fill(x + 11, horizon - 22, x + 15, horizon - 16, 0xFFFFA238);
        } else if (dimension.contains("end")) {
            for (int i = 0; i < 6; i++) {
                int px = x + 8 + i * Math.max(10, (w - 20) / 6);
                int ph = 8 + ((i * 7) % 26);
                g.fill(px, horizon - ph, px + 5, horizon + 2, 0xFF332C45);
            }
        } else if (biome.contains("desert") || biome.contains("badlands")) {
            g.fill(x + 7, horizon - 4, x + w / 2, horizon + 1, 0xFFD7A85C);
            g.fill(x + w / 3, horizon - 9, x + w - 8, horizon + 1, 0xFFC4834B);
        } else if (biome.contains("snow") || biome.contains("frozen") || biome.contains("ice")) {
            g.fill(x, horizon - 2, x + w, horizon + 5, 0xFFF0F5F4);
        }
    }

    private static void drawWeather(GuiGraphicsExtractor g, String weather, int x, int y, int w, int h) {
        String value = safe(weather);
        if (!value.equals("rain") && !value.equals("thunder")) return;
        for (int i = 0; i < 12; i++) {
            int rx = x + ((i * 37) % Math.max(1, w - 4));
            int ry = y + ((i * 19) % Math.max(1, h - 8));
            g.fill(rx, ry, rx + 1, ry + 6, 0xAA9FC7DD);
        }
        if (value.equals("thunder")) {
            int cx = x + w - 25;
            int cy = y + 8;
            g.fill(cx, cy, cx + 4, cy + 11, 0xFFFFF4A8);
            g.fill(cx - 4, cy + 9, cx + 3, cy + 14, 0xFFFFF4A8);
            g.fill(cx - 6, cy + 13, cx - 2, cy + 22, 0xFFFFF4A8);
        }
    }

    private static void drawEventSymbol(GuiGraphicsExtractor g, ComicLifePanel p, int cx, int groundY, int ink) {
        String type = safe(p.eventType);
        int body = type.equals("death") ? 0xFF8D3138 : 0xFF2E6B91;

        if (type.equals("dimension_travel")) {
            g.fill(cx - 14, groundY - 34, cx + 14, groundY + 4, 0xFF34203F);
            g.fill(cx - 9, groundY - 29, cx + 9, groundY - 1, 0xFF7D49A7);
            g.outline(cx - 14, groundY - 34, 28, 38, ink);
            return;
        }
        if (type.equals("milestone")) {
            g.fill(cx - 12, groundY - 23, cx + 12, groundY + 1, 0xFFFFCC4A);
            g.outline(cx - 12, groundY - 23, 24, 24, ink);
            g.fill(cx - 3, groundY - 17, cx + 3, groundY - 5, 0xFFFFFFFF);
            g.fill(cx - 9, groundY - 11, cx + 9, groundY - 9, 0xFFFFFFFF);
            return;
        }
        if (type.equals("death")) {
            g.fill(cx - 18, groundY - 7, cx + 17, groundY - 1, body);
            g.fill(cx - 11, groundY - 12, cx - 5, groundY + 4, body);
            g.fill(cx + 5, groundY - 12, cx + 11, groundY + 4, body);
            return;
        }

        g.fill(cx - 5, groundY - 36, cx + 6, groundY - 25, 0xFFD6AE8A);
        g.outline(cx - 5, groundY - 36, 11, 11, ink);
        g.fill(cx - 7, groundY - 25, cx + 8, groundY - 9, body);
        g.fill(cx - 11, groundY - 22, cx - 7, groundY - 9, body);
        g.fill(cx + 8, groundY - 22, cx + 12, groundY - 9, body);
        g.fill(cx - 6, groundY - 9, cx - 1, groundY + 4, 0xFF32323C);
        g.fill(cx + 2, groundY - 9, cx + 7, groundY + 4, 0xFF32323C);

        if (type.equals("near_death")) {
            g.outline(cx - 20, groundY - 42, 40, 49, 0xFFE14545);
        } else if (type.equals("deep_descent")) {
            g.fill(cx - 24, groundY - 42, cx - 20, groundY + 4, 0xFF292632);
            g.fill(cx + 20, groundY - 42, cx + 24, groundY + 4, 0xFF292632);
        } else if (type.equals("high_climb")) {
            g.fill(cx - 25, groundY + 1, cx + 26, groundY + 5, 0xFFC7CED2);
        }
    }

    private static int skyColor(String timeOfDay, String dimension) {
        String d = safe(dimension);
        if (d.contains("nether")) return 0xFF421A20;
        if (d.contains("end")) return 0xFF171424;
        return switch (safe(timeOfDay)) {
            case "night" -> 0xFF182A4A;
            case "dusk" -> 0xFFB56867;
            case "dawn" -> 0xFFD98B70;
            default -> 0xFF77A9C9;
        };
    }

    private static int groundColor(String biome, String dimension) {
        String d = safe(dimension);
        String b = safe(biome);
        if (d.contains("nether")) return 0xFF6A272A;
        if (d.contains("end")) return 0xFFB9AE70;
        if (b.contains("desert")) return 0xFFD7B66F;
        if (b.contains("badlands")) return 0xFFA96542;
        if (b.contains("snow") || b.contains("frozen")) return 0xFFE8EFEF;
        if (b.contains("swamp") || b.contains("mangrove")) return 0xFF52633B;
        return 0xFF64884D;
    }

    private static int foliageColor(String biome) {
        String b = safe(biome);
        if (b.contains("mangrove") || b.contains("swamp")) return 0xFF426348;
        if (b.contains("taiga")) return 0xFF365D50;
        if (b.contains("jungle")) return 0xFF397544;
        return 0xFF517E48;
    }

    private static int healthColor(float ratio) {
        if (ratio <= 0.2F) return 0xFFE03B3B;
        if (ratio <= 0.5F) return 0xFFE3913D;
        return 0xFF6FB25B;
    }

    private static void drawWrapped(GuiGraphicsExtractor g, Font font, String text, int x, int y, int width, int maxLines, int color) {
        if (text == null || text.isBlank()) return;
        int maxChars = Math.max(12, width / 6);
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int row = 0;
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                g.text(font, Component.literal(crop(line.toString(), maxChars)), x, y + row * 10, color);
                row++;
                if (row >= maxLines) return;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (row < maxLines && line.length() > 0) g.text(font, Component.literal(crop(line.toString(), maxChars)), x, y + row * 10, color);
    }

    private static String crop(String value, int max) {
        String s = value == null ? "" : value;
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
