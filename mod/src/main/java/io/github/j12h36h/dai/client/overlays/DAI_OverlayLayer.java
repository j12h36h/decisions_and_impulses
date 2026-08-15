package io.github.j12h36h.dai.client.overlays;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public abstract class DAI_OverlayLayer {

    private final String id;
    private final Identifier texture;
    private final DAI_OverlayAnchor anchor;
    private final int offsetX;
    private final int offsetY;
    private final int width;
    private final int height;
    private final int z;
    private final boolean interactable;
    private final String clickAction;
    private final boolean consumeClick;
    private final int tint;
    private final long insertionOrder;
    private int remainingTicks;

    protected DAI_OverlayLayer(
            String id,
            Identifier texture,
            DAI_OverlayAnchor anchor,
            int offsetX,
            int offsetY,
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
        this.id = id;
        this.texture = texture;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.z = z;
        this.remainingTicks = ticks;
        this.interactable = interactable;
        this.clickAction = clickAction == null ? "" : clickAction.trim();
        this.consumeClick = consumeClick;
        this.tint = tint;
        this.insertionOrder = insertionOrder;
    }

    public final String id() { return id; }
    public final Identifier texture() { return texture; }
    public final int width() { return width; }
    public final int height() { return height; }
    public final int z() { return z; }
    public final boolean interactable() { return interactable; }
    public final String clickAction() { return clickAction; }
    public final boolean consumeClick() { return consumeClick; }
    public final int tint() { return tint; }
    public final long insertionOrder() { return insertionOrder; }

    public final int left(int screenWidth) {
        return anchor.left(screenWidth, width, offsetX);
    }

    public final int top(int screenHeight) {
        return anchor.top(screenHeight, height, offsetY);
    }

    public final boolean contains(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!interactable) {
            return false;
        }
        int left = left(screenWidth);
        int top = top(screenHeight);
        return mouseX >= left
                && mouseX < left + width
                && mouseY >= top
                && mouseY < top + height;
    }

    public final boolean tickLifetime() {
        tickAnimation();
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        return remainingTicks == 0;
    }

    protected void tickAnimation() {
        // Static sprites have no animation state.
    }

    public abstract void extract(GuiGraphicsExtractor graphics);
}
