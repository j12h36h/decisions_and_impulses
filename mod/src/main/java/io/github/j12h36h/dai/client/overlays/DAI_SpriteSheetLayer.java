package io.github.j12h36h.dai.client.overlays;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class DAI_SpriteSheetLayer extends DAI_OverlayLayer {

    private final int frameWidth;
    private final int frameHeight;
    private final int frameCount;
    private final int columns;
    private final int frameTicks;
    private final boolean loop;
    private int currentFrame;
    private int frameClock;

    public DAI_SpriteSheetLayer(
            String id,
            Identifier texture,
            DAI_OverlayAnchor anchor,
            int x,
            int y,
            int width,
            int height,
            int frameWidth,
            int frameHeight,
            int frameCount,
            int columns,
            int frameTicks,
            boolean loop,
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
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;
        this.columns = Math.max(1, columns);
        this.frameTicks = Math.max(1, frameTicks);
        this.loop = loop;
    }

    @Override
    protected void tickAnimation() {
        if (frameCount <= 1) {
            return;
        }

        frameClock++;
        if (frameClock < frameTicks) {
            return;
        }

        frameClock = 0;
        if (currentFrame + 1 < frameCount) {
            currentFrame++;
        } else if (loop) {
            currentFrame = 0;
        }
    }

    @Override
    public void extract(GuiGraphicsExtractor graphics) {
        int frame = Math.min(Math.max(0, currentFrame), Math.max(0, frameCount - 1));
        int column = frame % columns;
        int row = frame / columns;
        int sheetRows = Math.max(1, (frameCount + columns - 1) / columns);
        int sheetWidth = frameWidth * columns;
        int sheetHeight = frameHeight * sheetRows;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture(),
                left(graphics.guiWidth()),
                top(graphics.guiHeight()),
                column * (float) frameWidth,
                row * (float) frameHeight,
                width(),
                height(),
                frameWidth,
                frameHeight,
                sheetWidth,
                sheetHeight,
                tint()
        );
    }
}
