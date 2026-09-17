package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Default player-facing PLAY hub for DAI Engine 4.1. */
public final class DAI_PlayScreen extends Screen {

    private final Screen parent;
    private DAI_PlayWorldAccess.WorldEntry recent;

    public DAI_PlayScreen(Screen parent) {
        super(Component.literal("Play"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        recent = DAI_PlayWorldAccess.mostRecent();
        int cardWidth = Math.min(420, Math.max(230, width - 40));
        int left = width / 2 - cardWidth / 2;
        int rowHeight = compact() ? 34 : 42;
        int gap = compact() ? 7 : 10;
        int top = compact() ? 58 : 72;

        addRenderableWidget(card(left, top, cardWidth, rowHeight,
                recent == null ? "CONTINUE · NO SAVE YET" : "CONTINUE · " + DAI_PlayWorldAccess.displayName(recent),
                button -> {
                    if (recent != null) DAI_PlayWorldAccess.open(this, recent);
                }, recent != null));

        int y2 = top + rowHeight + gap;
        addRenderableWidget(card(left, y2, cardWidth, rowHeight,
                "NEW SINGLEPLAYER · START SOMETHING NEW",
                button -> DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.SINGLEPLAYER_CREATE,
                        this,
                        () -> new DAI_NewSingleplayerScreen(this),
                        () -> this
                ), true));

        int y3 = y2 + rowHeight + gap;
        addRenderableWidget(card(left, y3, cardWidth, rowHeight,
                "MY WORLDS · ALL LOCAL SAVES",
                button -> DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.PLAY_WORLDS,
                        this,
                        () -> new DAI_WorldLibraryScreen(this),
                        () -> this
                ), false));

        addRenderableWidget(chip(width / 2 - 48, height - 28, 96, 20,
                "BACK", button -> onClose(), false));
    }

    private DAI_UniverseButton card(int x, int y, int w, int h, String label, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(
                x, y, w, h, Component.literal(label), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary()
        );
    }

    private DAI_UniverseButton chip(int x, int y, int w, int h, String label, Button.OnPress press, boolean primary) {
        return card(x, y, w, h, label, press, primary);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        int panelHalf = Math.min(236, Math.max(120, width / 2 - 12));
        graphics.fill(width / 2 - panelHalf, 36, width / 2 + panelHalf, height - 34, 0x7405070C);
        graphics.centeredText(font, Component.literal("PLAY"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("Continue, create, or choose a local world"), width / 2, 28, 0xFF9FB2BE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }

    private boolean compact() { return height < 330; }
}
