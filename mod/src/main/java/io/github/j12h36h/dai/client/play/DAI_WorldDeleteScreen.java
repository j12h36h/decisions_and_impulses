package io.github.j12h36h.dai.client.play;

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

/** DAI-native destructive confirmation for deleting a local world. */
public final class DAI_WorldDeleteScreen extends Screen {

    private final Screen parent;
    private final String worldName;
    private final Runnable deleteAction;

    public DAI_WorldDeleteScreen(Screen parent, String worldName, Runnable deleteAction) {
        super(Component.literal("Delete World"));
        this.parent = parent;
        this.worldName = worldName == null || worldName.isBlank() ? "World" : worldName;
        this.deleteAction = deleteAction == null ? () -> {} : deleteAction;
    }

    @Override protected void init() {
        super.init();
        int y = height / 2 + 28;
        addRenderableWidget(button(width / 2 - 108, y, 100, 22, "CANCEL", b -> onClose(), false));
        addRenderableWidget(button(width / 2 + 8, y, 100, 22, "DELETE", b -> deleteAction.run(), true));
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
        int boxWidth = Math.min(430, width - 32);
        int left = width / 2 - boxWidth / 2;
        int top = Math.max(48, height / 2 - 62);
        graphics.fill(left, top, left + boxWidth, top + 112, 0xD10A0D15);
        graphics.outline(left, top, boxWidth, 112, 0xFFB85A5A);
        graphics.centeredText(font, Component.literal("DELETE WORLD?"), width / 2, top + 14, 0xFFFF9C9C);
        graphics.centeredText(font, Component.literal(worldName), width / 2, top + 34, profile.text());
        graphics.centeredText(font, Component.literal("This permanently removes the local save."), width / 2, top + 55, 0xFFB7C7D0);
        graphics.centeredText(font, Component.literal("This cannot be undone."), width / 2, top + 69, 0xFFFFD782);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
