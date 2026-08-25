package io.github.j12h36h.dai.client.overlays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Lightweight runtime text element used by datapack-authored UI. */
public final class DAI_TextOverlayLayer extends DAI_OverlayLayer {
    private final String text;
    private final int textColor;

    public DAI_TextOverlayLayer(
            String id,
            DAI_OverlayAnchor anchor,
            int x,
            int y,
            int width,
            int z,
            int ticks,
            String text,
            int textColor,
            long insertionOrder
    ) {
        super(id, null, anchor, x, y, width, 9, z, ticks,
                false, "", false, 0xFFFFFFFF, insertionOrder);
        this.text = text == null ? "" : text;
        this.textColor = textColor;
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics) {
        var font = Minecraft.getInstance().font;
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(text), Math.max(1, width()));
        int actualHeight = Math.max(9, lines.size() * 9);
        if (!transformLocked()) {
            setSize(width(), actualHeight);
        }
        int left = left(graphics.guiWidth());
        int top = top(graphics.guiHeight());
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), left, top + i * 9, textColor);
        }
    }
}
