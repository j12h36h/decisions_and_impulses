package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** DAI-native replacement for Minecraft's disconnected / protocol error screen. */
public final class DAI_ConnectionLostScreen extends Screen {

    private final String heading;
    private final String detail;

    public DAI_ConnectionLostScreen(String heading, String detail) {
        super(Component.literal("Connection Interrupted"));
        this.heading = heading == null || heading.isBlank() ? "CONNECTION INTERRUPTED" : heading;
        this.detail = detail == null || detail.isBlank() ? "Minecraft ended the local world connection." : detail;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(button(width / 2 - 90, height / 2 + 58, 180, 22,
                "RETURN TO DAI UNIVERSE", b -> Minecraft.getInstance().gui.setScreen(new TitleScreen()), true));
    }

    private DAI_UniverseButton button(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
    }

    @Override public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        int boxWidth = Math.min(500, width - 30);
        int left = width / 2 - boxWidth / 2;
        int top = Math.max(42, height / 2 - 92);
        graphics.fill(left, top, left + boxWidth, top + 168, 0xD10A0D15);
        graphics.outline(left, top, boxWidth, 168, 0xFFB85A5A);
        graphics.centeredText(font, Component.literal("DAI WORLD CONNECTION"), width / 2, top + 14, 0xFFFFD782);
        graphics.centeredText(font, Component.literal(heading), width / 2, top + 34, 0xFFFF9C9C);
        graphics.textWithWordWrap(font, Component.literal(detail), left + 18, top + 58, boxWidth - 36, 0xFFD5E0E6);
        graphics.centeredText(font, Component.literal("The loading transition was cancelled safely."), width / 2, top + 122, 0xFF9FB2BE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
}
