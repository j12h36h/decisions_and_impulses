package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.packs.DAI_WorldResourcePackSelection;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;

/** DAI-native per-world resource-pack auto-activation editor. */
public final class DAI_WorldResourcePackScreen extends Screen {

    private final Screen parent;
    private final DAI_PlayWorldAccess.WorldEntry world;
    private final int page;
    private List<DAI_WorldResourcePackSelection.PackEntry> packs = List.of();

    public DAI_WorldResourcePackScreen(Screen parent, DAI_PlayWorldAccess.WorldEntry world) {
        this(parent, world, 0);
    }

    private DAI_WorldResourcePackScreen(Screen parent, DAI_PlayWorldAccess.WorldEntry world, int page) {
        super(Component.literal("World Resource Packs"));
        this.parent = parent;
        this.world = world;
        this.page = Math.max(0, page);
    }

    @Override
    protected void init() {
        super.init();
        packs = DAI_WorldResourcePackSelection.available(world == null ? "" : world.saveId());

        int panelW = Math.max(230, Math.min(560, width - 24));
        int left = width / 2 - panelW / 2;
        int top = 52;
        int rows = Math.max(1, Math.min(8, (height - top - 58) / 25));
        int maxPage = Math.max(0, (packs.size() - 1) / rows);
        int current = Math.min(page, maxPage);
        int start = Math.min(current * rows, packs.size());
        int end = Math.min(start + rows, packs.size());
        int y = top;

        for (int i = start; i < end; i++) {
            DAI_WorldResourcePackSelection.PackEntry pack = packs.get(i);
            String prefix = pack.selected() ? "✓ " : "□ ";
            DAI_UniverseButton button = button(
                    left, y, panelW, 20,
                    prefix + pack.label(),
                    ignored -> toggle(pack, current),
                    pack.selected(), true
            );
            addRenderableWidget(button);
            y += 25;
        }

        if (maxPage > 0) {
            int navY = height - 48;
            addRenderableWidget(button(left, navY, 72, 20, "◀ PREV",
                    ignored -> reopen(Math.max(0, current - 1)), false, current > 0));
            addRenderableWidget(button(left + panelW / 2 - 42, navY, 84, 20,
                    (current + 1) + " / " + (maxPage + 1), ignored -> {}, false, false));
            addRenderableWidget(button(left + panelW - 72, navY, 72, 20, "NEXT ▶",
                    ignored -> reopen(Math.min(maxPage, current + 1)), false, current < maxPage));
        }

        addRenderableWidget(button(width / 2 - 74, height - 24, 148, 18,
                "DONE", ignored -> onClose(), true, true));
    }

    private void toggle(DAI_WorldResourcePackSelection.PackEntry pack, int currentPage) {
        if (world == null || pack == null) return;
        DAI_WorldResourcePackSelection.setSelected(world.saveId(), pack.id(), !pack.selected());
        Minecraft.getInstance().gui.setScreen(new DAI_WorldResourcePackScreen(parent, world, currentPage));
    }

    private void reopen(int nextPage) {
        Minecraft.getInstance().gui.setScreen(new DAI_WorldResourcePackScreen(parent, world, nextPage));
    }

    private DAI_UniverseButton button(
            int x, int y, int w, int h, String text, Button.OnPress press, boolean primary, boolean active
    ) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseButton button = new DAI_UniverseButton(
                x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary()
        );
        button.active = active;
        return button;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        graphics.fill(8, 38, width - 8, height - 30, 0xB2080A10);
        graphics.outline(8, 38, width - 16, height - 68, 0xAAFF8A2A);
        graphics.centeredText(font, Component.literal("AUTO-ACTIVATE RESOURCE PACKS"), width / 2, 10, profile.text());
        graphics.centeredText(font,
                Component.literal(world == null ? "WORLD" : DAI_PlayWorldAccess.displayName(world)),
                width / 2, 24, 0xFFFFD782);
        graphics.centeredText(font,
                Component.literal("Selected packs are enabled before this world joins and restored after it unloads."),
                width / 2, 40, 0xFF9FB2BE);
        if (packs.isEmpty()) {
            graphics.centeredText(font, Component.literal("No installed resource packs were found."),
                    width / 2, 76, 0xFFB7C7D0);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
