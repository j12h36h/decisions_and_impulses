package io.github.j12h36h.dai.client.story.screen;

import com.google.gson.JsonObject;
import io.github.j12h36h.dai.client.menus.DAI_ScreenManager;
import io.github.j12h36h.dai.client.presentation.scene.DAI_SceneRenderer;
import io.github.j12h36h.dai.story.DAI_StoryProfileDefinition;
import io.github.j12h36h.dai.story.DAI_StoryProfileRegistry;
import io.github.j12h36h.dai.story.DAI_StoryVariables;
import io.github.j12h36h.dai.story.model.DAI_StoryArchive;
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

/** Generic archive browser styled entirely by the selected story profile. */
public final class DAI_StoryLibraryScreen extends Screen {
    private final Identifier profileId;
    private final DAI_StoryArchive archive;
    private final List<DAI_StorySession> sessions = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int scroll;

    public DAI_StoryLibraryScreen(Identifier profileId, DAI_StoryArchive archive) {
        super(Component.literal(profileId == null ? "" : profileId.toString()));
        this.profileId = profileId;
        this.archive = archive == null ? new DAI_StoryArchive() : archive;
        this.archive.normalize();
        sessions.addAll(this.archive.completed);
        sessions.addAll(this.archive.activeByContext.values());
        sessions.sort((a,b) -> Integer.compare(b.number, a.number));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        for (Hit hit : hits) {
            if (hit.contains(event.x(), event.y())) {
                DAI_ScreenManager.open(new DAI_StoryBookScreen(profileId, hit.session));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, sessions.size() - visibleRows());
        if (scrollY < 0) scroll = Math.min(max, scroll + 1);
        if (scrollY > 0) scroll = Math.max(0, scroll - 1);
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        DAI_StoryProfileDefinition profile = DAI_StoryProfileRegistry.get(profileId);
        if (profile == null) return;
        JsonObject viewer = profile.viewer();
        JsonObject library = DAI_StoryProfileDefinition.object(viewer, "library");
        int background = DAI_StoryProfileDefinition.color(library, "background", 0xE0101014);
        int text = DAI_StoryProfileDefinition.color(library, "text_color", 0xFFFFFFFF);
        int card = DAI_StoryProfileDefinition.color(library, "card_background", 0xD024242A);
        int border = DAI_StoryProfileDefinition.color(library, "card_border", 0xFF808080);
        int margin = Math.max(4, DAI_StoryProfileDefinition.integer(library, "margin", 18));
        int rowHeight = Math.max(24, DAI_StoryProfileDefinition.integer(library, "row_height", 42));
        String bgScene = DAI_StoryProfileDefinition.string(library, "background_scene", "");
        if (!bgScene.isBlank()) DAI_SceneRenderer.render(g, bgScene, 0, 0, width, height, partialTick, Map.of("profile.id", profileId.toString()));
        else g.fill(0, 0, width, height, background);

        Map<String,Object> rootVars = new LinkedHashMap<>();
        rootVars.put("profile.id", profileId.toString());
        rootVars.put("archive.completed_count", archive.completed.size());
        rootVars.put("archive.active_count", archive.activeByContext.size());
        rootVars.put("archive.session_count", sessions.size());
        String title = DAI_TemplateEngine.resolve(DAI_StoryProfileDefinition.string(library, "title", "{profile.id}"), rootVars);
        g.text(font, Component.literal(title), margin, margin, text);

        int y = margin + Math.max(font.lineHeight + 8, DAI_StoryProfileDefinition.integer(library, "header_height", 26));
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < sessions.size(); i++) {
            DAI_StorySession session = sessions.get(scroll + i);
            Map<String,Object> vars = DAI_StoryVariables.session(session);
            String label = DAI_TemplateEngine.resolve(
                    DAI_StoryProfileDefinition.string(library, "entry_text", "{session.title}"), vars);
            if (label.isBlank()) label = Integer.toString(session.number);
            g.fill(margin, y, width - margin, y + rowHeight - 4, card);
            g.outline(margin, y, Math.max(1, width - margin * 2), rowHeight - 4, border);
            g.text(font, Component.literal(label), margin + 8, y + Math.max(3, (rowHeight - font.lineHeight) / 2), text);
            hits.add(new Hit(margin, y, width - margin * 2, rowHeight - 4, session));
            y += rowHeight;
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int visibleRows() {
        DAI_StoryProfileDefinition profile = DAI_StoryProfileRegistry.get(profileId);
        JsonObject library = profile == null ? new JsonObject() : DAI_StoryProfileDefinition.object(profile.viewer(), "library");
        int margin = Math.max(4, DAI_StoryProfileDefinition.integer(library, "margin", 18));
        int rowHeight = Math.max(24, DAI_StoryProfileDefinition.integer(library, "row_height", 42));
        return Math.max(1, (height - margin * 2 - 30) / rowHeight);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    private record Hit(int x, int y, int w, int h, DAI_StorySession session) {
        boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
    }
}
