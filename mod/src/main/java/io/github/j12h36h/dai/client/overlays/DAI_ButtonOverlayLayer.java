package io.github.j12h36h.dai.client.overlays;

import io.github.j12h36h.dai.client.logics.input.DAI_MouseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Generic DAI-styled clickable overlay button for datapack-authored UI. */
public final class DAI_ButtonOverlayLayer extends DAI_OverlayLayer {
    private static final int BACKGROUND = 0xFF161A23;
    private static final int HOVER = 0xFF33234A;
    private static final int BORDER = 0xFFFFA62B;
    private static final int TEXT = 0xFFFFFFFF;
    private final String text;

    public DAI_ButtonOverlayLayer(String id, DAI_OverlayAnchor anchor, int x, int y, int width, int z, int ticks, String text, String clickAction, long insertionOrder) {
        super(id, null, anchor, x, y, width, 20, z, ticks, true, clickAction, true, 0xFFFFFFFF, insertionOrder);
        this.text = text == null ? "" : text;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics) {
        int left = left(graphics.guiWidth());
        int top = top(graphics.guiHeight());
        boolean hovered = boundsContain(DAI_MouseState.x(), DAI_MouseState.y(), graphics.guiWidth(), graphics.guiHeight());
        graphics.fill(left, top, left + width(), top + height(), hovered ? HOVER : BACKGROUND);
        graphics.outline(left, top, width(), height(), BORDER);
        var font = Minecraft.getInstance().font;
        Component component = Component.literal(text);
        int tx = left + Math.max(2, (width() - font.width(component)) / 2);
        int ty = top + (height() - 9) / 2;
        graphics.text(font, component, tx, ty, TEXT);
    }
}
