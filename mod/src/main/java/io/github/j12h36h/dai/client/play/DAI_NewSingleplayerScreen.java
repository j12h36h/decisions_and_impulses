package io.github.j12h36h.dai.client.play;

import io.github.j12h36h.dai.client.packs.DAI_PackBrowserScreen;
import io.github.j12h36h.dai.client.presentation.DAI_PresentationProfileService;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseButton;
import io.github.j12h36h.dai.client.presentation.DAI_UniverseShellRenderer;
import io.github.j12h36h.dai.client.presentation.shell.DAI_ShellScreenRouter;
import io.github.j12h36h.dai.client.title.DAI_ShellWorldRuntime;
import io.github.j12h36h.dai.runtime.DAI_StandaloneLaunchState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Three-choice singleplayer entry: Minecraft+DAI, authored experience, or vanilla. */
public final class DAI_NewSingleplayerScreen extends Screen {

    private final Screen parent;

    public DAI_NewSingleplayerScreen(Screen parent) {
        super(Component.literal("New Singleplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int widthCard = Math.min(440, Math.max(230, width - 40));
        int left = width / 2 - widthCard / 2;
        int rowHeight = height < 330 ? 36 : 44;
        int gap = height < 330 ? 7 : 10;
        int y = height < 330 ? 56 : 70;

        addRenderableWidget(card(left, y, widthCard, rowHeight,
                "MINECRAFT + DAI · CHOOSE ADDONS",
                button -> DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.MINECRAFT_DAI_CREATE,
                        this,
                        () -> new DAI_MinecraftDaiCreateScreen(this),
                        () -> this
                ), true));
        y += rowHeight + gap;

        addRenderableWidget(card(left, y, widthCard, rowHeight,
                "DAI EXPERIENCES · INSTALLED GAMES",
                button -> DAI_ShellScreenRouter.open(
                        DAI_ShellScreenRouter.EXPERIENCE_CREATE,
                        this,
                        () -> new DAI_ExperienceCreateScreen(this),
                        () -> new DAI_PackBrowserScreen(this)
                ), false));
        y += rowHeight + gap;

        addRenderableWidget(card(left, y, widthCard, rowHeight,
                "VANILLA MINECRAFT · STANDARD SINGLEPLAYER",
                button -> {
                    // Explicit empty selection means truly vanilla. A null
                    // selection is reserved for legacy automatic addon policy.
                    DAI_StandaloneLaunchState.prepare(java.util.Set.of());
                    DAI_VanillaWorldScreens.openCreate(this);
                }, false));

        addRenderableWidget(card(width / 2 - 48, height - 28, 96, 20,
                "BACK", button -> onClose(), false));
    }

    private DAI_UniverseButton card(int x, int y, int w, int h, String text, Button.OnPress press, boolean primary) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        return new DAI_UniverseButton(x, y, w, h, Component.literal(text), press,
                DAI_UniverseButton.Shape.CHIP,
                primary ? profile.primary() : profile.secondary());
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        DAI_PresentationProfileService.Profile profile = DAI_PresentationProfileService.selected();
        DAI_UniverseShellRenderer.render(graphics, width, height, profile, System.nanoTime());
        int half = Math.min(244, Math.max(120, width / 2 - 12));
        graphics.fill(width / 2 - half, 36, width / 2 + half, height - 34, 0x7405070C);
        graphics.centeredText(font, Component.literal("WHAT DO YOU WANT TO PLAY?"), width / 2, 14, profile.text());
        graphics.centeredText(font, Component.literal("Choose Minecraft + DAI, an installed experience, or vanilla"), width / 2, 28, 0xFF9FB2BE);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void onClose() { DAI_StandaloneLaunchState.clear(); Minecraft.getInstance().gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return DAI_ShellWorldRuntime.isShellActive(); }
}
