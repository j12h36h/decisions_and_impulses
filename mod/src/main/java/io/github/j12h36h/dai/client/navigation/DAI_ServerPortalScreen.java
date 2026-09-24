package io.github.j12h36h.dai.client.navigation;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.client.title.DAI_TitleActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** DAI-owned public/saved multiplayer landing screens. */
public final class DAI_ServerPortalScreen extends Screen {
    public enum Mode { PUBLIC, SAVED }

    private final Screen parent;
    private final Mode mode;

    public DAI_ServerPortalScreen(Screen parent, Mode mode) {
        super(Component.literal(mode == Mode.PUBLIC ? "Public" : "Saved"));
        this.parent = parent;
        this.mode = mode == null ? Mode.SAVED : mode;
    }

    @Override
    protected void init() {
        super.init();
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        if (mode == Mode.SAVED) {
            addRenderableWidget(new DAI_UniverseButton(
                    width / 2 - 115, height / 2 - 14, 230, 28,
                    Component.literal("OPEN SAVED / DIRECT CONNECT"),
                    b -> DAI_TitleActionDispatcher.openReflective(this, "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen"),
                    DAI_UniverseButton.Shape.CHIP, profile.primary()
            ));
        }
        addRenderableWidget(new DAI_UniverseButton(
                width / 2 - 48, height - 30, 96, 20,
                Component.literal("BACK"), b -> onClose(),
                DAI_UniverseButton.Shape.CHIP, profile.secondary()
        ));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        String title = mode == Mode.PUBLIC ? "PUBLIC" : "SAVED";
        String subtitle = mode == Mode.PUBLIC
                ? "Public DAI Experience servers will appear here when a catalog is available."
                : "Saved Minecraft servers and direct IP connections remain user controlled.";
        graphics.centeredText(font, Component.literal(title), width / 2, 18, profile.text());
        graphics.centeredText(font, Component.literal(subtitle), width / 2, 34, 0xFF9FB2BE);
        if (mode == Mode.PUBLIC) {
            graphics.centeredText(font, Component.literal("NO PUBLIC SERVERS AVAILABLE"), width / 2, height / 2 - 4, 0xFFB7C7D0);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
