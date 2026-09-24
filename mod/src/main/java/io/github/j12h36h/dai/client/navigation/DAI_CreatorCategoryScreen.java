package io.github.j12h36h.dai.client.navigation;

import io.github.j12h36h.dai.client.creator.DAI_CreatorRuntime;
import io.github.j12h36h.dai.client.creator.DAI_CreatorScreen;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** Category-specific entry surface for DAI's schema-driven creator. */
public final class DAI_CreatorCategoryScreen extends Screen {

    public enum Category {
        OBJECT("OBJECT", "Sword Art Online-style object authority", List.of(
                new Tool("ITEM", "decisions_and_impulses:item"),
                new Tool("BLOCK", "decisions_and_impulses:block"),
                new Tool("ENTITY", "decisions_and_impulses:entity"),
                new Tool("WEAPON", "decisions_and_impulses:weapon"),
                new Tool("PROJECTILE", "decisions_and_impulses:projectile"),
                new Tool("VEHICLE", "decisions_and_impulses:vehicle")
        )),
        LOGIC("LOGIC", "Blueprint-style gameplay logic authoring", List.of(
                new Tool("ACTION", "decisions_and_impulses:action"),
                new Tool("REACTION", "decisions_and_impulses:reaction"),
                new Tool("STATE", "decisions_and_impulses:state"),
                new Tool("QUEST", "decisions_and_impulses:quest"),
                new Tool("RULESET", "decisions_and_impulses:ruleset"),
                new Tool("DIALOGUE", "decisions_and_impulses:dialogue")
        )),
        VISUAL("VISUAL", "Sprites, models, animation, particles and presentation", List.of(
                new Tool("ANIMATION", "decisions_and_impulses:animation"),
                new Tool("PARTICLE", "decisions_and_impulses:particle"),
                new Tool("RENDER", "decisions_and_impulses:render_profile"),
                new Tool("SCENE", "decisions_and_impulses:scene_environment"),
                new Tool("CINEMATIC", "decisions_and_impulses:cinematic"),
                new Tool("HUD", "decisions_and_impulses:hud")
        )),
        CORE("CORE", "Sound, data, recipes and nonvisual game assets", List.of(
                new Tool("SOUND", "decisions_and_impulses:sound"),
                new Tool("MUSIC", "decisions_and_impulses:music"),
                new Tool("RECIPE", "decisions_and_impulses:recipe"),
                new Tool("LOOT", "decisions_and_impulses:loot"),
                new Tool("CURRENCY", "decisions_and_impulses:currency"),
                new Tool("BIOME", "decisions_and_impulses:biome"),
                new Tool("INPUT", "decisions_and_impulses:input_profile")
        ));

        private final String title;
        private final String subtitle;
        private final List<Tool> tools;

        Category(String title, String subtitle, List<Tool> tools) {
            this.title = title;
            this.subtitle = subtitle;
            this.tools = tools;
        }
    }

    private final Screen parent;
    private final Category category;

    public DAI_CreatorCategoryScreen(Screen parent, Category category) {
        super(Component.literal(category == null ? "Creator" : category.title));
        this.parent = parent;
        this.category = category == null ? Category.OBJECT : category;
    }

    @Override
    protected void init() {
        super.init();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        int cols = width < 520 ? 2 : 3;
        int buttonW = Math.max(92, Math.min(150, (width - 48) / cols - 8));
        int buttonH = 34;
        int totalW = cols * buttonW + (cols - 1) * 8;
        int x0 = width / 2 - totalW / 2;
        int y0 = 66;
        for (int i = 0; i < category.tools.size(); i++) {
            Tool tool = category.tools.get(i);
            int col = i % cols;
            int row = i / cols;
            addRenderableWidget(new DAI_UniverseButton(
                    x0 + col * (buttonW + 8), y0 + row * 42, buttonW, buttonH,
                    Component.literal(tool.label()), b -> open(tool),
                    DAI_UniverseButton.Shape.CHIP,
                    i == 0 ? profile.primary() : profile.secondary()
            ));
        }
        addRenderableWidget(new DAI_UniverseButton(
                width / 2 - 48, height - 30, 96, 20,
                Component.literal("BACK"), b -> onClose(),
                DAI_UniverseButton.Shape.CHIP, profile.secondary()
        ));
    }

    private void open(Tool tool) {
        DAI_CreatorRuntime.selectSchema(tool.schema());
        Minecraft.getInstance().gui.setScreen(new DAI_CreatorScreen());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal(category.title), width / 2, 18, profile.text());
        graphics.centeredText(font, Component.literal(category.subtitle), width / 2, 32, 0xFF9FB2BE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private record Tool(String label, String schema) {}
}
