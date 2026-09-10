package io.github.j12h36h.dai.client.menus;

import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class DAI_StyledButton extends Button.Plain {

    private DAI_ButtonStyle style;
    private boolean selectedStyle;
    private float textScale = 1.0F;

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

    /**
     * Scales only this button's label while leaving its hitbox and border at
     * normal GUI coordinates. Creator uses this at compact GUI scales so
     * readable labels do not have to be aggressively truncated.
     */
    public void setTextScale(float textScale) {
        if (!Float.isFinite(textScale)) {
            this.textScale = 1.0F;
            return;
        }
        this.textScale = Math.max(0.55F, Math.min(1.0F, textScale));
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

        int textColor = style.text().isBlank()
                ? getFGColor()
                : color(style.text(), getFGColor());

        if (textScale >= 0.999F) {
            graphics.centeredText(
                    Minecraft.getInstance().font,
                    getMessage(),
                    getX() + getWidth() / 2,
                    textY,
                    textColor
            );
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(textScale, textScale);
        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                Math.round((getX() + getWidth() / 2.0F) / textScale),
                Math.round(textY / textScale),
                textColor
        );
        graphics.pose().popMatrix();
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
