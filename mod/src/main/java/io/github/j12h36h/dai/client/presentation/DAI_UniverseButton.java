package io.github.j12h36h.dai.client.presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Custom shell control; never uses Minecraft's vanilla button texture. */
public final class DAI_UniverseButton extends Button.Plain {

    public enum Shape { CHIP, NODE }

    private final Shape shape;
    private final int accent;

    public DAI_UniverseButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Button.OnPress onPress
    ) {
        this(x, y, width, height, message, onPress, Shape.CHIP,
                DAI_PresentationProfileService.selected().primary());
    }

    public DAI_UniverseButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Button.OnPress onPress,
            Shape shape,
            int accent
    ) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.shape = shape == null ? Shape.CHIP : shape;
        this.accent = accent;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (shape == Shape.NODE) {
            renderNode(graphics);
        } else {
            renderChip(graphics);
        }
    }

    private void renderChip(GuiGraphicsExtractor graphics) {
        boolean hovered = isHoveredOrFocused();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int background = hovered ? 0xD5221830 : 0xA80A0D14;
        int edge = hovered ? accent : withAlpha(accent, 0x88);

        graphics.fill(x, y, x + w, y + h, background);
        graphics.fill(x, y, x + (hovered ? 4 : 2), y + h, edge);
        graphics.outline(x, y, w, h, edge);
        if (hovered) graphics.outline(x - 1, y - 1, w + 2, h + 2, withAlpha(accent, 0x55));

        int textY = y + Math.max(0, (h - Minecraft.getInstance().font.lineHeight) / 2);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(), x + w / 2, textY, 0xFFF7F0FF);
    }

    private void renderNode(GuiGraphicsExtractor graphics) {
        boolean hovered = isHoveredOrFocused();
        int cx = getX() + getWidth() / 2;
        int cy = getY() + getHeight() / 2 - 5;
        int radius = Math.max(9, Math.min(getWidth(), getHeight()) / 4);
        double seconds = System.nanoTime() / 1_000_000_000.0D;
        int pulse = hovered ? 2 + (int)Math.round((Math.sin(seconds * 5.0D) + 1.0D) * 1.5D) : 0;

        int edge = hovered ? accent : withAlpha(accent, 0xAA);
        int glow = withAlpha(accent, hovered ? 0x45 : 0x22);
        graphics.fill(cx - radius - 5 - pulse, cy - 2, cx + radius + 6 + pulse, cy + 3, glow);
        graphics.fill(cx - 2, cy - radius - 5 - pulse, cx + 3, cy + radius + 6 + pulse, glow);
        graphics.outline(cx - radius - pulse, cy - radius - pulse, (radius + pulse) * 2, (radius + pulse) * 2, edge);
        graphics.outline(cx - radius + 4, cy - radius + 4, Math.max(1, radius * 2 - 8), Math.max(1, radius * 2 - 8), withAlpha(edge, 0x99));
        graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, edge);

        int labelY = Math.min(getY() + getHeight() - Minecraft.getInstance().font.lineHeight, cy + radius + 7);
        graphics.centeredText(Minecraft.getInstance().font, getMessage(), cx, labelY, 0xFFF7F0FF);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
