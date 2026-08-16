package io.github.j12h36h.dai.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small destructive action beside an experience-save row. */
public final class DAI_ExperienceDeleteButton extends Button.Plain {

    private final DAI_TitleScreenDefinition.SaveBrowserDefinition style;

    public DAI_ExperienceDeleteButton(
            int x,
            int y,
            int width,
            int height,
            DAI_TitleScreenDefinition.SaveBrowserDefinition style,
            Button.OnPress onPress
    ) {
        super(x, y, width, height, Component.literal("×"), onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        boolean hovered = isHoveredOrFocused();
        int background = hovered ? style.deleteHover() : style.deleteBackground();
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), style.deleteBorder());

        int textY = getY() + Math.max(0, (getHeight() - Minecraft.getInstance().font.lineHeight) / 2);
        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + getWidth() / 2,
                textY,
                0xFFFFFFFF
        );
    }
}
