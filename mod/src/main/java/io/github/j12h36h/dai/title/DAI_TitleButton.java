package io.github.j12h36h.dai.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * JSON-driven title button with a title-safe texture icon.
 *
 * Minecraft 26.x does not permit constructing ItemStacks before item
 * components are bound / a level exists. The title screen necessarily exists
 * before a level, so "item" icons intentionally resolve to the item's normal
 * resource-pack texture path instead of allocating an ItemStack. This keeps
 * the main menu safe while still allowing vanilla/resource-pack item art to be
 * used and animated.
 */
public final class DAI_TitleButton extends Button.Plain {

    private static final int ICON_SIZE = 16;

    private final DAI_TitleScreenDefinition.ButtonDefinition definition;
    private final Identifier iconTexture;

    public DAI_TitleButton(
            int x,
            int y,
            DAI_TitleScreenDefinition.ButtonDefinition definition,
            Button.OnPress onPress
    ) {
        super(
                x,
                y,
                definition.width(),
                definition.height(),
                Component.literal(definition.label()),
                onPress,
                DEFAULT_NARRATION
        );
        this.definition = definition;
        this.iconTexture = resolveIconTexture(definition.icon());
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        DAI_TitleScreenDefinition.StyleDefinition style = definition.style();
        boolean hovered = isHoveredOrFocused();
        int background = hovered ? style.hover() : style.background();

        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                background
        );

        graphics.outline(
                getX(),
                getY(),
                getWidth(),
                getHeight(),
                style.border()
        );

        int textY = getY()
                + Math.max(0, (getHeight() - Minecraft.getInstance().font.lineHeight) / 2);

        int textX = getX() + getWidth() / 2;
        if (iconTexture != null) {
            textX += 7;
            extractIcon(graphics, hovered);
        }

        graphics.centeredText(
                Minecraft.getInstance().font,
                getMessage(),
                textX,
                textY,
                style.text()
        );
    }

    private void extractIcon(
            GuiGraphicsExtractor graphics,
            boolean hovered
    ) {
        if (iconTexture == null) return;

        DAI_TitleScreenDefinition.IconDefinition iconDefinition = definition.icon();
        int iconX = getX() + iconDefinition.offsetX();
        int iconY = getY() + (getHeight() - ICON_SIZE) / 2 + iconDefinition.offsetY();

        float scale = iconDefinition.scale();
        float rotation = 0.0F;
        float dx = 0.0F;
        float dy = 0.0F;

        if (hovered) {
            DAI_TitleScreenDefinition.HoverAnimation animation = definition.hoverAnimation();
            double seconds = System.nanoTime() / 1_000_000_000.0D;
            double phase = seconds * animation.speed() * Math.PI * 2.0D;

            switch (animation.type()) {
                case "spin" -> rotation = (float) (phase % (Math.PI * 2.0D));
                case "bob" -> dy = (float) (Math.sin(phase) * animation.amount());
                case "pulse" -> scale *= 1.0F
                        + (float) ((Math.sin(phase) + 1.0D) * 0.05D * animation.amount());
                case "orbit" -> {
                    dx = (float) (Math.cos(phase) * animation.amount());
                    dy = (float) (Math.sin(phase) * animation.amount());
                    rotation = (float) phase;
                }
                default -> {
                    // No animation.
                }
            }
        }

        float centerX = iconX + ICON_SIZE / 2.0F;
        float centerY = iconY + ICON_SIZE / 2.0F;

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX + dx, centerY + dy);
        if (rotation != 0.0F) {
            graphics.pose().rotate(rotation);
        }
        if (scale != 1.0F) {
            graphics.pose().scale(scale, scale);
        }
        graphics.pose().translate(-centerX, -centerY);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                iconTexture,
                iconX,
                iconY,
                0.0F,
                0.0F,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                0xFFFFFFFF
        );

        graphics.pose().popMatrix();
    }

    private static Identifier resolveIconTexture(
            DAI_TitleScreenDefinition.IconDefinition definition
    ) {
        if (definition == null) return null;
        return DAI_TitleIconTextures.resolve(definition.type(), definition.id());
    }

}
