package io.github.j12h36h.dai.client.overlays;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class DAI_StaticSpriteLayer extends DAI_OverlayLayer {

    public DAI_StaticSpriteLayer(
            String id,
            Identifier texture,
            DAI_OverlayAnchor anchor,
            int x,
            int y,
            int width,
            int height,
            int z,
            int ticks,
            boolean interactable,
            String clickAction,
            boolean consumeClick,
            int tint,
            long insertionOrder
    ) {
        super(id, texture, anchor, x, y, width, height, z, ticks,
                interactable, clickAction, consumeClick, tint, insertionOrder);
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics) {
        int left = left(graphics.guiWidth());
        int top = top(graphics.guiHeight());
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture(),
                left,
                top,
                0.0F,
                0.0F,
                width(),
                height(),
                width(),
                height(),
                tint()
        );
    }
}
