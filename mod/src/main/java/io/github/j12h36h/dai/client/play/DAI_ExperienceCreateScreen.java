package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceLauncher;
import io.github.j12h36h.dai.client.packs.DAI_PackBrowserScreen;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.experience.DAI_ExperienceDefinition;
import io.github.j12h36h.dai.experience.DAI_ExperienceRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Generic experience picker generated entirely from installed DAI experience definitions. */
public final class DAI_ExperienceCreateScreen extends Screen {

    private final Screen parent;
    private final int page;
    private List<DAI_ExperienceDefinition> experiences = List.of();

    public DAI_ExperienceCreateScreen(Screen parent) { this(parent, 0); }
    private DAI_ExperienceCreateScreen(Screen parent, int page) {
        super(Component.literal("DAI Experience"));
        this.parent = parent;
        this.page = Math.max(0, page);
    }

    @Override protected void init() {
        super.init();
        experiences = new ArrayList<>(DAI_ExperienceRepository.reloadSelectable().values());
        experiences = experiences.stream()
                .sorted(Comparator.comparingInt(DAI_ExperienceDefinition::priority).reversed()
                        .thenComparing(DAI_ExperienceDefinition::id))
                .toList();

        int rows = Math.max(1, Math.min(4, (height - 132) / 38));
        int maxPage = Math.max(0, (experiences.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, experiences.size());
        int end = Math.min(start + rows, experiences.size());
        int rowWidth = Math.min(520, Math.max(250, width - 36));
        int left = width / 2 - rowWidth / 2;
        int y = 58;

        for (int i = start; i < end; i++) {
            DAI_ExperienceDefinition experience = experiences.get(i);
            int actionWidth = Math.min(84, Math.max(64, rowWidth / 5));
            int labelWidth = rowWidth - actionWidth * 2 - 8;
            addRenderableWidget(button(left, y, labelWidth, 28, display(experience), b -> {}, false));
            addRenderableWidget(button(left + labelWidth + 4, y, actionWidth, 28, "CREATE",
                    b -> DAI_ExperienceLauncher.launchNew(this, experience.id()), true));
            addRenderableWidget(button(left + labelWidth + actionWidth + 8, y, actionWidth, 28, "CONTINUE",
                    b -> DAI_ExperienceLauncher.continueLast(this, experience.id()), false));
            y += 38;
        }

        if (maxPage > 0) {
            int navY = height - 54;
            addRenderableWidget(button(width / 2 - 106, navY, 62, 20, "◀ PREV",
                    b -> reopen(Math.max(0, current - 1)), false));
            addRenderableWidget(button(width / 2 - 39, navY, 78, 20,
                    (current + 1) + " / " + (maxPage + 1), b -> {}, false));
            addRenderableWidget(button(width / 2 + 44, navY, 62, 20, "NEXT ▶",
                    b -> reopen(Math.min(maxPage, current + 1)), false));
        }

        addRenderableWidget(button(width / 2 - 112, height - 28, 104, 20, "BACK", b -> onClose(), false));
        addRenderableWidget(button(width / 2 + 8, height - 28, 104, 20, "DISCOVER", b ->
                Minecraft.getInstance().gui.setScreen(new DAI_PackBrowserScreen(this)), false));
    }

    private void reopen(int next) { Minecraft.getInstance().gui.setScreen(new DAI_ExperienceCreateScreen(parent, next)); }

    private DAI_UniverseButton button(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
    }

    private static String display(DAI_ExperienceDefinition definition) {
        String value = definition.saveName();
        if (value == null || value.isBlank()) value = definition.id();
        return value == null ? "DAI EXPERIENCE" : value.toUpperCase(java.util.Locale.ROOT);
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.centeredText(font, Component.literal("CREATE FROM INSTALLED EXPERIENCE"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("One installed experience is selected for each new world"), width / 2, 28, 0xFF9FB2BE);
        if (experiences.isEmpty()) graphics.centeredText(font, Component.literal("No installed DAI experiences found · use DISCOVER to install one"), width / 2, 86, 0xFFB7C7D0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
