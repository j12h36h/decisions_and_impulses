package io.github.j12h36h.dai.client.comiclife.screen;

import io.github.j12h36h.dai.client.comiclife.ComicLifeRuntime;
import io.github.j12h36h.dai.comiclife.model.ComicLifeArchive;
import io.github.j12h36h.dai.comiclife.model.ComicLifeLife;
import io.github.j12h36h.dai.comiclife.story.ComicCompiler;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ComicLifeLibraryScreen extends Screen {
    private static final int PER_PAGE = 6;
    private int page;
    private final List<ComicLifeLife> shown = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    public ComicLifeLibraryScreen() {
        super(Component.literal("ComicLife Library"));
    }

    @Override
    protected void init() {
        page = Math.max(0, page);
        rebuild();
    }

    private void rebuild() {
        shown.clear();
        ComicLifeArchive archive = ComicLifeRuntime.archive();
        if (archive == null) return;
        archive.normalize();
        ComicLifeLife active = ComicLifeRuntime.currentLife();
        if (active != null) shown.add(active);
        archive.completed.stream()
                .filter(life -> life != null)
                .sorted(Comparator.comparingInt((ComicLifeLife life) -> life.number).reversed())
                .forEach(shown::add);
        int maxPage = shown.isEmpty() ? 0 : Math.max(0, (shown.size() - 1) / PER_PAGE);
        if (page > maxPage) page = maxPage;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != 0) return super.mouseClicked(event, doubleClick);
        for (Hit hit : hits) {
            if (hit.contains(mouseX, mouseY)) {
                if (hit.kind == 0 && hit.life != null) {
                    DAI_ScreenManager.open(new ComicLifeBookScreen(hit.life));
                } else if (hit.kind == 1 && page > 0) {
                    page--;
                    rebuild();
                } else if (hit.kind == 2 && (page + 1) * PER_PAGE < shown.size()) {
                    page++;
                    rebuild();
                } else if (hit.kind == 3) {
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        g.fill(0, 0, width, height, 0xFF18131B);
        int bookW = Math.min(620, Math.max(320, width - 36));
        int bookH = Math.min(390, Math.max(230, height - 32));
        int left = (width - bookW) / 2;
        int top = (height - bookH) / 2;
        g.fill(left, top, left + bookW, top + bookH, 0xFFF0E1BE);
        g.outline(left, top, bookW, bookH, 0xFF2B1F22);
        g.fill(left + bookW / 2 - 2, top + 5, left + bookW / 2 + 2, top + bookH - 5, 0xFF9C7856);

        g.text(font, Component.literal("COMICLIFE"), left + 18, top + 15, 0xFF36262A);
        g.text(font, Component.literal("Every life leaves a volume."), left + 18, top + 29, 0xFF6A4C49);
        g.text(font, Component.literal("Volumes: " + ComicLifeRuntime.completedCount()), left + bookW / 2 + 16, top + 15, 0xFF36262A);
        g.text(font, Component.literal("Page " + (page + 1) + " / " + Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE)), left + bookW / 2 + 16, top + 29, 0xFF6A4C49);

        int start = page * PER_PAGE;
        int end = Math.min(shown.size(), start + PER_PAGE);
        int cardTop = top + 52;
        int cardH = 72;
        int gap = 8;
        for (int i = start; i < end; i++) {
            ComicLifeLife life = shown.get(i);
            int local = i - start;
            int column = local % 2;
            int row = local / 2;
            int x = left + 16 + column * (bookW / 2);
            int y = cardTop + row * (cardH + gap);
            int w = bookW / 2 - 32;
            int bg = life.completed ? 0xFFE1CCA3 : 0xFFE7D9B8;
            g.fill(x, y, x + w, y + cardH, bg);
            g.outline(x, y, w, cardH, 0xFF5A4140);
            g.fill(x + 7, y + 7, x + 34, y + cardH - 7, life.completed ? 0xFF5A3440 : 0xFF36556A);
            g.text(font, Component.literal("L" + ComicCompiler.roman(Math.max(1, life.number))), x + 11, y + 28, 0xFFFFF0CF);
            g.text(font, Component.literal(life.completed ? life.title : "CURRENT LIFE"), x + 42, y + 10, 0xFF2D2225);
            g.text(font, Component.literal(ComicCompiler.friendly(life.lastBiome)), x + 42, y + 25, 0xFF6A4C49);
            g.text(font, Component.literal(ComicCompiler.formatDuration(life.durationMs()) + " • " + life.events.size() + " events"), x + 42, y + 40, 0xFF6A4C49);
            g.text(font, Component.literal(life.completed ? crop(life.deathMessage, 41) : "The final page is still unwritten."), x + 42, y + 54, 0xFF3E3133);
            hits.add(new Hit(x, y, w, cardH, 0, life));
        }

        if (shown.isEmpty()) {
            g.text(font, Component.literal("Your first volume begins when gameplay begins."), left + 22, top + 92, 0xFF4C393B);
            g.text(font, Component.literal("Explore, survive, discover, and let the ending compile the story."), left + 22, top + 108, 0xFF6A4C49);
        }

        int navY = top + bookH - 29;
        drawButton(g, left + 16, navY, 72, 18, "< PREV", page > 0, 1, null);
        drawButton(g, left + bookW - 88, navY, 72, 18, "NEXT >", (page + 1) * PER_PAGE < shown.size(), 2, null);
        drawButton(g, left + bookW / 2 - 34, navY, 68, 18, "CLOSE", true, 3, null);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean enabled, int kind, ComicLifeLife life) {
        g.fill(x, y, x + w, y + h, enabled ? 0xFF5A3440 : 0xFF9A8C7A);
        g.outline(x, y, w, h, 0xFF2B1F22);
        g.text(font, Component.literal(text), x + 7, y + 5, enabled ? 0xFFFFE8C7 : 0xFFD3C7B2);
        if (enabled) hits.add(new Hit(x, y, w, h, kind, life));
    }

    private static String crop(String value, int max) {
        String s = value == null ? "" : value;
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private record Hit(int x, int y, int w, int h, int kind, ComicLifeLife life) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
}
