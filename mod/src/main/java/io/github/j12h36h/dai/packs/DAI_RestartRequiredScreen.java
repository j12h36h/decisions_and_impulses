package io.github.j12h36h.dai.packs;

import io.github.j12h36h.dai.title.DAI_TitleActionDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** Blocks returning to play after managed pack changes until the client exits. */
public final class DAI_RestartRequiredScreen extends Screen {

    private final Screen browser;
    private final Screen parent;

    public DAI_RestartRequiredScreen(Screen browser, Screen parent) {
        super(Component.literal("Restart Required"));
        this.browser = browser;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int center = width / 2;
        int y = height / 2 + 35;

        addRenderableWidget(Button.builder(
                        Component.literal("RETURN TO PACKS"),
                        button -> Minecraft.getInstance().gui.setScreen(browser)
                )
                .bounds(center - 155, y, 145, 22)
                .build());

        addRenderableWidget(Button.builder(
                        Component.literal("EXIT MINECRAFT"),
                        button -> DAI_TitleActionDispatcher.stopMinecraft()
                )
                .bounds(center + 10, y, 145, 22)
                .build());
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fillGradient(0, 0, width, height, 0xFF100B0B, 0xFF2A1715);
        graphics.centeredText(font, Component.literal("RESTART REQUIRED"), width / 2, height / 2 - 45, 0xFFFFC27D);
        graphics.centeredText(
                font,
                Component.literal("Official pack changes were made."),
                width / 2,
                height / 2 - 20,
                0xFFFFFFFF
        );
        graphics.centeredText(
                font,
                Component.literal("Exit Minecraft completely so D.A.I. can register and load the new pack set on launch."),
                width / 2,
                height / 2 - 4,
                0xFFD9C8BC
        );
        graphics.centeredText(
                font,
                Component.literal("No world will be opened with a half-applied pack configuration."),
                width / 2,
                height / 2 + 12,
                0xFFBBA79C
        );
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {}

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(browser == null ? parent : browser);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
