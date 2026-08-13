package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.menus.system.DAI_ButtonStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class DAI_StyledButton extends Button.Plain {

    private DAI_ButtonStyle style;
    private boolean selectedStyle;

    public DAI_StyledButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Button.OnPress onPress,
            DAI_ButtonStyle style
    ) {
        super(
                x,
                y,
                width,
                height,
                message,
                onPress,
                DEFAULT_NARRATION
        );

        this.style =
                style == null
                        ? DAI_ButtonStyle.EMPTY
                        : style;
    }

    public void setStyle(
            DAI_ButtonStyle style
    ) {
        this.style =
                style == null
                        ? DAI_ButtonStyle.EMPTY
                        : style;
    }

    public void setSelectedStyle(
            boolean selectedStyle
    ) {
        this.selectedStyle =
                selectedStyle;
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        if (style.isEmpty()) {
            super.extractContents(
                    graphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
            return;
        }

        String backgroundOverride =
                style.background();

        if (
                selectedStyle
                        && !style.selected().isBlank()
        ) {
            backgroundOverride =
                    style.selected();
        }

        if (
                isHoveredOrFocused()
                        && !style.hover().isBlank()
        ) {
            backgroundOverride =
                    style.hover();
        }

        if (backgroundOverride.isBlank()) {
            extractDefaultSprite(
                    graphics
            );
        } else {
            graphics.fill(
                    getX(),
                    getY(),
                    getX() + getWidth(),
                    getY() + getHeight(),
                    color(
                            backgroundOverride,
                            0xFF263545
                    )
            );
        }

        if (!style.border().isBlank()) {
            graphics.outline(
                    getX(),
                    getY(),
                    getWidth(),
                    getHeight(),
                    color(
                            style.border(),
                            0xFFFFFFFF
                    )
            );
        }

        int textY =
                getY()
                        + Math.max(
                        0,
                        (getHeight()
                                - Minecraft.getInstance().font.lineHeight)
                                / 2
                );

        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + getWidth() / 2,
                textY,
                style.text().isBlank()
                        ? getFGColor()
                        : color(
                        style.text(),
                        getFGColor()
                )
        );
    }

    private static int color(
            String value,
            int fallback
    ) {

        if (value == null || value.isBlank()) {
            return fallback;
        }

        String normalized =
                value.trim();

        if (normalized.startsWith("#")) {
            normalized =
                    normalized.substring(1);
        }

        try {
            if (normalized.length() == 6) {
                return 0xFF000000
                        | Integer.parseUnsignedInt(
                        normalized,
                        16
                );
            }

            if (normalized.length() == 8) {
                return (int) Long.parseLong(
                        normalized,
                        16
                );
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the requested fallback.
        }

        return fallback;
    }
}
