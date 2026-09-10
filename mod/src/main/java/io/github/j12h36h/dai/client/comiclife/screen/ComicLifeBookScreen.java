package io.github.j12h36h.dai.client.comiclife.screen;

import io.github.j12h36h.dai.client.comiclife.render.SceneSketchRenderer;
import io.github.j12h36h.dai.comiclife.model.ComicLifeLife;
import io.github.j12h36h.dai.comiclife.model.ComicLifePage;
import io.github.j12h36h.dai.comiclife.story.ComicCompiler;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;

public final class ComicLifeBookScreen extends Screen {
    private final ComicLifeLife life;
    private final List<ComicLifePage> pages;
    private int pageIndex;
    private final Hit[] nav = new Hit[3];

    public ComicLifeBookScreen(ComicLifeLife life) {
        super(Component.literal("ComicLife"));
        this.life = life;
        this.pages = life != null && life.completed && life.pages != null && !life.pages.isEmpty()
                ? List.copyOf(life.pages)
                : ComicCompiler.previewPages(life);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) return super.mouseClicked(event, doubleClick);
        if (nav[0] != null && nav[0].contains(mouseX, mouseY) && pageIndex > 0) { pageIndex--; return true; }
        if (nav[1] != null && nav[1].contains(mouseX, mouseY)) { DAI_ScreenManager.open(new ComicLifeLibraryScreen()); return true; }
        if (nav[2] != null && nav[2].contains(mouseX, mouseY) && pageIndex + 1 < pages.size()) { pageIndex++; return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF161117);
        if (life == null || pages.isEmpty()) {
            g.text(font, Component.literal("This volume has no readable pages."), 20, 20, 0xFFFFE8C7);
            return;
        }
        ComicLifePage page = pages.get(Math.max(0, Math.min(pageIndex, pages.size() - 1)));
        int bookW = Math.min(650, Math.max(340, width - 30));
        int bookH = Math.min(420, Math.max(250, height - 26));
        int left = (width - bookW) / 2;
        int top = (height - bookH) / 2;

        if ("cover".equals(page.kind)) renderCover(g, page, left, top, bookW, bookH);
        else if ("back".equals(page.kind)) renderBack(g, page, left, top, bookW, bookH);
        else renderStory(g, page, left, top, bookW, bookH);

        int navY = top + bookH - 25;
        nav[0] = drawButton(g, left + 10, navY, 64, 17, "< PAGE", pageIndex > 0);
        nav[1] = drawButton(g, left + bookW / 2 - 34, navY, 68, 17, "LIBRARY", true);
        nav[2] = drawButton(g, left + bookW - 74, navY, 64, 17, "PAGE >", pageIndex + 1 < pages.size());
    }

    private void renderCover(GuiGraphicsExtractor g, ComicLifePage page, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF4B2934);
        g.outline(x, y, w, h, 0xFFE7C886);
        g.outline(x + 6, y + 6, w - 12, h - 12, 0xFF9E794F);
        g.fill(x + 18, y + 22, x + w - 18, y + 62, 0xFF21181D);
        g.text(font, Component.literal("COMICLIFE"), x + 30, y + 34, 0xFFF3D79C);
        g.text(font, Component.literal(page.heading), x + 30, y + 80, 0xFFFFEDC3);
        g.text(font, Component.literal(crop(page.subheading, 70)), x + 30, y + 98, 0xFFF3D79C);

        int artX = x + 30;
        int artY = y + 126;
        int artW = w - 60;
        int artH = Math.max(80, h - 190);
        g.fill(artX, artY, artX + artW, artY + artH, 0xFF262A38);
        g.outline(artX, artY, artW, artH, 0xFFF3D79C);
        for (int i = 0; i < 8; i++) {
            int bx = artX + 12 + ((i * 73) % Math.max(20, artW - 28));
            int bh = 15 + ((i * 19) % Math.max(16, artH - 28));
            g.fill(bx, artY + artH - bh - 7, bx + 11, artY + artH - 7, i % 2 == 0 ? 0xFF6C5963 : 0xFF3D5060);
        }
        g.fill(artX + artW / 2 - 8, artY + artH / 2 - 25, artX + artW / 2 + 9, artY + artH / 2 - 8, 0xFFD5AD89);
        g.fill(artX + artW / 2 - 11, artY + artH / 2 - 8, artX + artW / 2 + 12, artY + artH / 2 + 24, 0xFF8C3E4E);
        g.text(font, Component.literal(life.completed ? "A COMPLETE VOLUME" : "CURRENT VOLUME"), x + 30, y + h - 53, 0xFFF3D79C);
        g.text(font, Component.literal(ComicCompiler.formatDuration(life.durationMs()) + " • " + life.events.size() + " recorded events"), x + 30, y + h - 39, 0xFFD8BE8E);
    }

    private void renderStory(GuiGraphicsExtractor g, ComicLifePage page, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFF0E1BE);
        g.outline(x, y, w, h, 0xFF2B1F22);
        g.fill(x + w / 2 - 1, y + 4, x + w / 2 + 2, y + h - 31, 0xFFC3A47A);
        g.text(font, Component.literal(page.heading), x + 13, y + 11, 0xFF34262A);
        g.text(font, Component.literal(crop(page.subheading, 45)), x + w / 2 + 10, y + 11, 0xFF6A4C49);

        int panelY = y + 31;
        int panelH = h - 67;
        if (page.panels.size() == 1) {
            SceneSketchRenderer.render(g, font, page.panels.getFirst(), x + 18, panelY, w - 36, panelH, pageIndex * 2);
        } else {
            int panelW = w / 2 - 30;
            if (!page.panels.isEmpty()) SceneSketchRenderer.render(g, font, page.panels.get(0), x + 16, panelY, panelW, panelH, pageIndex * 2 - 1);
            if (page.panels.size() > 1) SceneSketchRenderer.render(g, font, page.panels.get(1), x + w / 2 + 14, panelY, panelW, panelH, pageIndex * 2);
        }
    }

    private void renderBack(GuiGraphicsExtractor g, ComicLifePage page, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF30242A);
        g.outline(x, y, w, h, 0xFFE4C78E);
        g.text(font, Component.literal(page.heading), x + 26, y + 35, 0xFFFFE7B6);
        drawWrapped(g, page.subheading, x + 26, y + 58, w - 52, 4, 0xFFE2C99A);
        g.fill(x + 26, y + 116, x + w - 26, y + 118, 0xFF9B754E);
        g.text(font, Component.literal("Life " + life.number + " • " + ComicCompiler.formatDuration(life.durationMs())), x + 26, y + 132, 0xFFD8BE8E);
        g.text(font, Component.literal("Events remembered: " + life.events.size()), x + 26, y + 147, 0xFFD8BE8E);
        g.text(font, Component.literal("Biomes crossed: " + life.seenBiomes.size()), x + 26, y + 162, 0xFFD8BE8E);
        g.text(font, Component.literal("Dimensions crossed: " + life.seenDimensions.size()), x + 26, y + 177, 0xFFD8BE8E);
        g.text(font, Component.literal("Farthest from first panel: " + life.farthestDistance + " blocks"), x + 26, y + 192, 0xFFD8BE8E);
        if (life.completed) {
            g.text(font, Component.literal("The next life begins on respawn."), x + 26, y + h - 55, 0xFFFFE7B6);
        }
    }

    private Hit drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean enabled) {
        g.fill(x, y, x + w, y + h, enabled ? 0xFF573640 : 0xFF756B65);
        g.outline(x, y, w, h, 0xFF21181D);
        g.text(font, Component.literal(text), x + 6, y + 4, enabled ? 0xFFFFE8C7 : 0xFFBFB5A7);
        return enabled ? new Hit(x, y, w, h) : null;
    }

    private void drawWrapped(GuiGraphicsExtractor g, String text, int x, int y, int width, int maxLines, int color) {
        if (text == null || text.isBlank()) return;
        int maxChars = Math.max(14, width / 6);
        StringBuilder line = new StringBuilder();
        int row = 0;
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                g.text(font, Component.literal(line.toString()), x, y + row * 11, color);
                row++;
                if (row >= maxLines) return;
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (row < maxLines && line.length() > 0) g.text(font, Component.literal(line.toString()), x, y + row * 11, color);
    }

    private static String crop(String value, int max) {
        String s = value == null ? "" : value;
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private record Hit(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
}
