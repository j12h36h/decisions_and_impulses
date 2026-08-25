package io.github.j12h36h.dai.client.overlays;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public abstract class DAI_OverlayLayer {

    private final String id;
    private final Identifier texture;
    private final DAI_OverlayAnchor anchor;
    private int offsetX;
    private int offsetY;
    private int width;
    private int height;
    private int z;
    private boolean interactable;
    private boolean transformLocked;
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
    public final int offsetX() { return offsetX; }
    public final int offsetY() { return offsetY; }
    public final int width() { return width; }
    public final int height() { return height; }
    public final int z() { return z; }
    public final boolean interactable() { return interactable; }
    public final boolean transformLocked() { return transformLocked; }
    public final String clickAction() { return clickAction; }
    public final boolean consumeClick() { return consumeClick; }
    public final int tint() { return tint; }
    public final long insertionOrder() { return insertionOrder; }


    public final boolean setPosition(int x, int y) {
        if (transformLocked) return false;
        offsetX = x;
        offsetY = y;
        return true;
    }

    public final boolean moveBy(int dx, int dy) {
        if (transformLocked) return false;
        offsetX += dx;
        offsetY += dy;
        return true;
    }

    public final boolean setSize(int newWidth, int newHeight) {
        if (transformLocked || newWidth <= 0 || newHeight <= 0) return false;
        width = newWidth;
        height = newHeight;
        return true;
    }

    public final boolean setZ(int newZ) {
        if (transformLocked) return false;
        z = newZ;
        return true;
    }

    public final void setInteractable(boolean value) {
        interactable = value;
    }

    public final void setTransformLocked(boolean value) {
        transformLocked = value;
    }

    public final double centerX(int screenWidth) {
        return left(screenWidth) + width / 2.0D;
    }

    public final double centerY(int screenHeight) {
        return top(screenHeight) + height / 2.0D;
    }

    public final double distanceTo(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        double dx = centerX(screenWidth) - mouseX;
        double dy = centerY(screenHeight) - mouseY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public final int left(int screenWidth) {
        return anchor.left(screenWidth, width, offsetX);
    }

    public final int top(int screenHeight) {
        return anchor.top(screenHeight, height, offsetY);
    }

    public final boolean boundsContain(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int left = left(screenWidth);
        int top = top(screenHeight);
        return mouseX >= left
                && mouseX < left + width
                && mouseY >= top
                && mouseY < top + height;
    }

    public final boolean contains(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        return interactable && boundsContain(mouseX, mouseY, screenWidth, screenHeight);
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
