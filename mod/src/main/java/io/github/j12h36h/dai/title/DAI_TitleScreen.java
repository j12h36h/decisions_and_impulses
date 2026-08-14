package io.github.j12h36h.dai.title;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

/** JSON-driven replacement for Minecraft's vanilla title screen. */
public final class DAI_TitleScreen extends Screen {

    private final DAI_TitleScreenDefinition definition;

    public DAI_TitleScreen(DAI_TitleScreenDefinition definition) {
        super(Component.literal("Decisions & Impulses"));
        this.definition = definition == null
                ? DAI_TitleScreenDefinition.fallback("decisions_and_impulses:fallback")
                : definition;
    }

    @Override
    protected void init() {
        super.init();

        for (DAI_TitleScreenDefinition.ButtonDefinition button : definition.buttons()) {
            int x = resolveX(button);
            int y = resolveY(button);

            DAI_TitleButton widget = new DAI_TitleButton(
                    x,
                    y,
                    button,
                    pressed -> DAI_TitleActionDispatcher.run(this, button)
            );

            addRenderableWidget(widget);
        }

        DAI_Core.debug(
                "<DAI>: Initialized JSON title screen '{}' with {} button(s).",
                definition.id(),
                definition.buttons().size()
        );
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fillGradient(
                0,
                0,
                width,
                height,
                definition.backgroundTop(),
                definition.backgroundBottom()
        );

        graphics.centeredText(
                font,
                Component.literal(definition.title()),
                width / 2,
                Math.max(20, height / 2 - 132),
                definition.titleColor()
        );

        graphics.centeredText(
                font,
                Component.literal(definition.subtitle()),
                width / 2,
                Math.max(35, height / 2 - 112),
                definition.subtitleColor()
        );

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // This screen draws its own JSON-configured background.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int resolveX(DAI_TitleScreenDefinition.ButtonDefinition button) {
        return switch (button.anchor()) {
            case "top_left", "left" -> button.x();
            case "top_right", "right" -> width - button.width() - button.x();
            default -> width / 2 - button.width() / 2 + button.x();
        };
    }

    private int resolveY(DAI_TitleScreenDefinition.ButtonDefinition button) {
        return switch (button.anchor()) {
            case "top_left", "top_right", "top" -> button.y();
            case "bottom_left", "bottom_right", "bottom" -> height - button.height() - button.y();
            default -> height / 2 + button.y();
        };
    }
}
