package io.github.j12h36h.dai.client.story.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.story.DAI_StoryCompiler;
import io.github.j12h36h.dai.story.DAI_StoryProfileDefinition;
import io.github.j12h36h.dai.story.DAI_StoryProfileRegistry;
import io.github.j12h36h.dai.story.DAI_StoryVariables;
import io.github.j12h36h.dai.story.model.DAI_StoryPage;
import io.github.j12h36h.dai.story.model.DAI_StoryPanel;
import io.github.j12h36h.dai.story.model.DAI_StorySession;
import io.github.j12h36h.dai.util.DAI_TemplateEngine;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic paged story viewer. Layout, labels, colors and scene choices are profile data. */
public final class DAI_StoryBookScreen extends Screen {
    private final Identifier profileId;
    private final DAI_StorySession session;
    private final List<DAI_StoryPage> pages;
    private int pageIndex;
    private Hit previous, library, next;

    public DAI_StoryBookScreen(Identifier profileId, DAI_StorySession session) {
        super(Component.literal(profileId == null ? "" : profileId.toString()));
        this.profileId = profileId;
        this.session = session;
        DAI_StoryProfileDefinition profile = DAI_StoryProfileRegistry.get(profileId);
        this.pages = session != null && session.completed && session.pages != null && !session.pages.isEmpty()
                ? List.copyOf(session.pages)
                : DAI_StoryCompiler.preview(session, profile);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (previous != null && previous.contains(event.x(), event.y()) && pageIndex > 0) { pageIndex--; return true; }
        if (library != null && library.contains(event.x(), event.y())) {
            io.github.j12h36h.dai.story.model.DAI_StoryArchive archive = io.github.j12h36h.dai.client.story.DAI_StoryRuntime.archive(profileId.toString());
            if (archive != null) DAI_ScreenManager.open(new DAI_StoryLibraryScreen(profileId, archive));
            return true;
        }
        if (next != null && next.contains(event.x(), event.y()) && pageIndex + 1 < pages.size()) { pageIndex++; return true; }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        previous = library = next = null;
        DAI_StoryProfileDefinition profile = DAI_StoryProfileRegistry.get(profileId);
        if (profile == null || session == null || pages.isEmpty()) return;
        JsonObject viewer = profile.viewer();
        JsonObject book = DAI_StoryProfileDefinition.object(viewer, "book");
        int backdrop = DAI_StoryProfileDefinition.color(book, "background", 0xE0101014);
        String backdropScene = DAI_StoryProfileDefinition.string(book, "background_scene", "");
        if (!backdropScene.isBlank()) DAI_SceneRenderer.render(g, backdropScene, 0, 0, width, height, partialTick, DAI_StoryVariables.session(session));
        else g.fill(0, 0, width, height, backdrop);

        int pageW = Math.min(width - 20, Math.max(120, DAI_StoryProfileDefinition.integer(book, "width", 640)));
        int pageH = Math.min(height - 20, Math.max(90, DAI_StoryProfileDefinition.integer(book, "height", 400)));
        int left = (width - pageW) / 2;
        int top = (height - pageH) / 2;
        DAI_StoryPage page = pages.get(Math.max(0, Math.min(pageIndex, pages.size() - 1)));
        renderPage(g, profile, viewer, page, left, top, pageW, pageH, partialTick);
        renderNavigation(g, viewer, left, top, pageW, pageH);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderPage(GuiGraphicsExtractor g, DAI_StoryProfileDefinition profile, JsonObject viewer, DAI_StoryPage page,
                            int x, int y, int w, int h, float partialTick) {
        JsonObject style = DAI_StoryProfileDefinition.object(viewer, page.kind);
        if (style.size() == 0 && "story".equals(page.kind)) style = DAI_StoryProfileDefinition.object(viewer, "story_page");
        int background = DAI_StoryProfileDefinition.color(style, "background", 0xF0E8E8E8);
        int border = DAI_StoryProfileDefinition.color(style, "border", 0xFF303030);
        int textColor = DAI_StoryProfileDefinition.color(style, "text_color", 0xFF202020);
        String scene = DAI_StoryProfileDefinition.string(style, "scene", "");
        Map<String,Object> vars = DAI_StoryVariables.session(session);
        if (!scene.isBlank()) DAI_SceneRenderer.render(g, scene, x, y, w, h, partialTick, vars);
        else g.fill(x, y, x + w, y + h, background);
        g.outline(x, y, w, h, border);

        int headingX = x + DAI_StoryProfileDefinition.integer(style, "heading_x", 12);
        int headingY = y + DAI_StoryProfileDefinition.integer(style, "heading_y", 10);
        if (!page.heading.isBlank()) g.text(font, Component.literal(page.heading), headingX, headingY, textColor);
        if (!page.subheading.isBlank()) g.text(font, Component.literal(page.subheading), headingX,
                headingY + DAI_StoryProfileDefinition.integer(style, "subheading_offset_y", 14), textColor);
        if (page.panels == null || page.panels.isEmpty()) return;

        List<Rect> layout = layout(viewer, page.panels.size(), x, y, w, h);
        for (int i = 0; i < page.panels.size(); i++) {
            Rect rect = layout.get(Math.min(i, layout.size() - 1));
            renderPanel(g, viewer, page.panels.get(i), rect, partialTick);
        }
    }

    private void renderPanel(GuiGraphicsExtractor g, JsonObject viewer, DAI_StoryPanel panel, Rect rect, float partialTick) {
        JsonObject style = DAI_StoryProfileDefinition.object(viewer, "panel");
        int background = DAI_StoryProfileDefinition.color(style, "background", 0xFF202020);
        int border = DAI_StoryProfileDefinition.color(style, "border", 0xFFFFFFFF);
        int textColor = DAI_StoryProfileDefinition.color(style, "text_color", 0xFFFFFFFF);
        Map<String,Object> vars = new LinkedHashMap<>(DAI_StoryVariables.session(session));
        if (panel.fields != null) panel.fields.forEach((k,v) -> { vars.put(k,v); vars.put("event." + k,v); });
        vars.put("event.id", panel.eventId); vars.put("event.importance", panel.importance);
        String scene = panel.scene;
        if (scene != null && !scene.isBlank()) DAI_SceneRenderer.render(g, DAI_TemplateEngine.resolve(scene, vars), rect.x, rect.y, rect.w, rect.h, partialTick, vars);
        else g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, background);
        g.outline(rect.x, rect.y, rect.w, rect.h, border);
        int padding = Math.max(1, DAI_StoryProfileDefinition.integer(style, "padding", 5));
        int captionY = rect.y + padding;
        if (!panel.caption.isBlank()) g.text(font, Component.literal(crop(panel.caption, Math.max(8, rect.w / 6))), rect.x + padding, captionY, textColor);
        if (!panel.narration.isBlank()) {
            int narrationY = rect.y + rect.h - font.lineHeight - padding;
            g.text(font, Component.literal(crop(panel.narration, Math.max(8, rect.w / 6))), rect.x + padding, narrationY, textColor);
        }
    }

    private List<Rect> layout(JsonObject viewer, int count, int x, int y, int w, int h) {
        JsonObject layouts = DAI_StoryProfileDefinition.object(viewer, "layouts");
        JsonArray array = layouts.has(Integer.toString(count)) && layouts.get(Integer.toString(count)).isJsonArray()
                ? layouts.getAsJsonArray(Integer.toString(count)) : new JsonArray();
        ArrayList<Rect> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject r = element.getAsJsonObject();
            double rx = DAI_StoryProfileDefinition.number(r, "x", 0.0D);
            double ry = DAI_StoryProfileDefinition.number(r, "y", 0.0D);
            double rw = DAI_StoryProfileDefinition.number(r, "width", 1.0D);
            double rh = DAI_StoryProfileDefinition.number(r, "height", 1.0D);
            result.add(new Rect(x + (int)Math.round(rx * w), y + (int)Math.round(ry * h),
                    Math.max(1, (int)Math.round(rw * w)), Math.max(1, (int)Math.round(rh * h))));
        }
        if (!result.isEmpty()) return result;
        int gap = 6;
        int topInset = 34;
        int usableH = Math.max(20, h - topInset - 32);
        int panelW = Math.max(1, (w - gap * (count + 1)) / Math.max(1, count));
        for (int i = 0; i < count; i++) result.add(new Rect(x + gap + i * (panelW + gap), y + topInset, panelW, usableH));
        return result;
    }

    private void renderNavigation(GuiGraphicsExtractor g, JsonObject viewer, int x, int y, int w, int h) {
        JsonObject nav = DAI_StoryProfileDefinition.object(viewer, "navigation");
        int bg = DAI_StoryProfileDefinition.color(nav, "background", 0xD0303030);
        int fg = DAI_StoryProfileDefinition.color(nav, "text_color", 0xFFFFFFFF);
        int buttonW = Math.max(24, DAI_StoryProfileDefinition.integer(nav, "button_width", 68));
        int buttonH = Math.max(12, DAI_StoryProfileDefinition.integer(nav, "button_height", 18));
        int py = y + h - buttonH - 6;
        String prevText = DAI_StoryProfileDefinition.string(nav, "previous", "<");
        String libraryText = DAI_StoryProfileDefinition.string(nav, "library", "=");
        String nextText = DAI_StoryProfileDefinition.string(nav, "next", ">");
        if (pageIndex > 0) previous = button(g, x + 6, py, buttonW, buttonH, prevText, bg, fg);
        library = button(g, x + w / 2 - buttonW / 2, py, buttonW, buttonH, libraryText, bg, fg);
        if (pageIndex + 1 < pages.size()) next = button(g, x + w - buttonW - 6, py, buttonW, buttonH, nextText, bg, fg);
    }

    private Hit button(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int bg, int fg) {
        g.fill(x, y, x + w, y + h, bg); g.outline(x, y, w, h, fg);
        g.centeredText(font, Component.literal(text), x + w / 2, y + Math.max(1, (h - font.lineHeight) / 2), fg);
        return new Hit(x,y,w,h);
    }

    private static String crop(String value, int max) {
        String s = value == null ? "" : value;
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private record Rect(int x, int y, int w, int h) {}
    private record Hit(int x, int y, int w, int h) { boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;} }
}
