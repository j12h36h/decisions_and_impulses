package io.github.j12h36h.dai.client.overlays;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.client.logics.core.DAI_DebugProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DAI_OverlayManager {

    private static final Map<String, DAI_OverlayLayer> LAYERS =
            new LinkedHashMap<>();

    private static long insertionSequence;

    private static int lastGuiWidth;
    private static int lastGuiHeight;

    private DAI_OverlayManager() {
        // Utility class.
    }

    public static long nextInsertionOrder() {
        return ++insertionSequence;
    }

    public static void put(DAI_OverlayLayer layer) {
        if (layer == null || layer.id() == null || layer.id().isBlank()) {
            return;
        }
        LAYERS.put(layer.id(), layer);
    }

    public static boolean remove(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return LAYERS.remove(id.trim()) != null;
    }

    public static void clear() {
        LAYERS.clear();
        lastGuiWidth = 0;
        lastGuiHeight = 0;
    }

    public static int size() {
        return LAYERS.size();
    }


    public static DAI_OverlayLayer get(String id) {
        if (id == null || id.isBlank()) return null;
        return LAYERS.get(id.trim());
    }

    public static int guiWidth() {
        if (lastGuiWidth > 0) return lastGuiWidth;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getGuiScaledWidth()
                : 0;
    }

    public static int guiHeight() {
        if (lastGuiHeight > 0) return lastGuiHeight;
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getWindow() != null
                ? minecraft.getWindow().getGuiScaledHeight()
                : 0;
    }

    public static boolean setPosition(String id, int x, int y) {
        DAI_OverlayLayer layer = get(id);
        return layer != null && layer.setPosition(x, y);
    }

    public static boolean move(String id, int dx, int dy) {
        DAI_OverlayLayer layer = get(id);
        return layer != null && layer.moveBy(dx, dy);
    }

    public static boolean setSize(String id, int width, int height) {
        DAI_OverlayLayer layer = get(id);
        return layer != null && layer.setSize(width, height);
    }

    public static boolean setZ(String id, int z) {
        DAI_OverlayLayer layer = get(id);
        return layer != null && layer.setZ(z);
    }

    public static boolean setTransformLocked(String id, boolean locked) {
        DAI_OverlayLayer layer = get(id);
        if (layer == null) return false;
        layer.setTransformLocked(locked);
        return true;
    }

    public static boolean setInteractable(String id, boolean interactable) {
        DAI_OverlayLayer layer = get(id);
        if (layer == null) return false;
        layer.setInteractable(interactable);
        return true;
    }

    public static boolean clampToScreen(String id) {
        DAI_OverlayLayer layer = get(id);
        if (layer == null || lastGuiWidth <= 0 || lastGuiHeight <= 0 || layer.transformLocked()) return false;
        int left = layer.left(lastGuiWidth);
        int top = layer.top(lastGuiHeight);
        int dx = 0;
        int dy = 0;
        if (left < 0) dx = -left;
        else if (left + layer.width() > lastGuiWidth) dx = lastGuiWidth - (left + layer.width());
        if (top < 0) dy = -top;
        else if (top + layer.height() > lastGuiHeight) dy = lastGuiHeight - (top + layer.height());
        return layer.moveBy(dx, dy);
    }

    public static boolean moveAwayFrom(String id, double x, double y, double speed) {
        DAI_OverlayLayer layer = get(id);
        if (layer == null || layer.transformLocked() || lastGuiWidth <= 0 || lastGuiHeight <= 0) return false;
        double dx = layer.centerX(lastGuiWidth) - x;
        double dy = layer.centerY(lastGuiHeight) - y;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001D) {
            dx = 1.0D;
            dy = 0.0D;
            length = 1.0D;
        }
        double safeSpeed = Math.max(0.0D, speed);
        int moveX = (int) Math.round(dx / length * safeSpeed);
        int moveY = (int) Math.round(dy / length * safeSpeed);
        if (moveX == 0 && moveY == 0 && safeSpeed > 0.0D) moveX = dx >= 0.0D ? 1 : -1;
        boolean moved = layer.moveBy(moveX, moveY);
        clampToScreen(id);
        return moved;
    }

    public static String hoveredIds(double mouseX, double mouseY) {
        if (lastGuiWidth <= 0 || lastGuiHeight <= 0 || LAYERS.isEmpty()) return "-";
        StringBuilder result = new StringBuilder();
        for (DAI_OverlayLayer layer : LAYERS.values()) {
            if (!layer.boundsContain(mouseX, mouseY, lastGuiWidth, lastGuiHeight)) continue;
            if (!result.isEmpty()) result.append(',');
            result.append(layer.id());
        }
        return result.isEmpty() ? "-" : result.toString();
    }

    public static String debugSnapshot() {
        if (LAYERS.isEmpty()) return "[]";
        StringBuilder result = new StringBuilder("[");
        int emitted = 0;
        for (DAI_OverlayLayer layer : LAYERS.values()) {
            if (emitted++ > 0) result.append(';');
            if (emitted > 12) { result.append("..."); break; }
            result.append(layer.id())
                    .append('@').append(layer.offsetX()).append(',').append(layer.offsetY())
                    .append(' ').append(layer.width()).append('x').append(layer.height())
                    .append(" z").append(layer.z())
                    .append(layer.transformLocked() ? " L" : "")
                    .append(layer.interactable() ? " I" : "");
        }
        return result.append(']').toString();
    }

    /** Returns whether an overlay with the authored id is currently active. */
    public static boolean contains(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return LAYERS.containsKey(id.trim());
    }

    public static void tick() {
        if (LAYERS.isEmpty()) {
            return;
        }

        List<String> expired = new ArrayList<>();
        for (DAI_OverlayLayer layer : LAYERS.values()) {
            if (layer.tickLifetime()) {
                expired.add(layer.id());
            }
        }
        expired.forEach(LAYERS::remove);
    }

    /** Renders only during normal in-game HUD extraction. */
    public static void extractHud(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker
    ) {
        if (Minecraft.getInstance().gui.screen() != null) {
            return;
        }
        extract(graphics);
    }

    /** Renders after Screen's final tooltip/subtitle extraction. */
    public static void extractForScreen(GuiGraphicsExtractor graphics) {
        if (Minecraft.getInstance().gui.screen() == null) {
            return;
        }
        extract(graphics);
    }

    private static void extract(GuiGraphicsExtractor graphics) {
        if (graphics == null || LAYERS.isEmpty()) {
            return;
        }

        lastGuiWidth = graphics.guiWidth();
        lastGuiHeight = graphics.guiHeight();

        List<DAI_OverlayLayer> ordered = new ArrayList<>(LAYERS.values());
        ordered.sort(
                Comparator.comparingInt(DAI_OverlayLayer::z)
                        .thenComparingLong(DAI_OverlayLayer::insertionOrder)
        );

        for (DAI_OverlayLayer layer : ordered) {
            graphics.nextStratum();
            layer.extract(graphics);
            if (DAI_Config.isDebuggingEnabled()) {
                drawDebugBounds(graphics, layer);
            }
        }
    }

    /**
     * Fires every intersected interactable layer from topmost to bottommost.
     * Returning true means one layer requested click consumption.
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (LAYERS.isEmpty()) {
            return false;
        }

        if (lastGuiWidth <= 0 || lastGuiHeight <= 0) {
            return false;
        }

        int screenWidth = lastGuiWidth;
        int screenHeight = lastGuiHeight;

        List<DAI_OverlayLayer> ordered = new ArrayList<>(LAYERS.values());
        ordered.sort(
                Comparator.comparingInt(DAI_OverlayLayer::z)
                        .thenComparingLong(DAI_OverlayLayer::insertionOrder)
                        .reversed()
        );

        List<DAI_ActionDefinition> clickActions = new ArrayList<>();
        boolean consumed = false;

        for (DAI_OverlayLayer layer : ordered) {
            if (!layer.contains(mouseX, mouseY, screenWidth, screenHeight)) {
                continue;
            }

            DAI_DebugProbe.record(
                    "overlay_click",
                    "id=" + layer.id() + " x=" + Math.round(mouseX) + " y=" + Math.round(mouseY)
            );

            if (!layer.clickAction().isBlank()) {
                List<DAI_ActionDefinition> resolved =
                        DAI_ActionResolver.resolve(layer.clickAction());
                if (resolved != null && !resolved.isEmpty()) {
                    clickActions.addAll(resolved);
                    DAI_ExperienceRuntime.onOverlayActionDispatched(layer.clickAction());
                }
            }

            if (layer.consumeClick()) {
                consumed = true;
                break;
            }
        }

        if (!clickActions.isEmpty()) {
            DAI_AutomationLogic.interruptWorkForMenuAction();
            DAI_ActionQueue.interruptAndDispatch(clickActions);
        }

        return consumed;
    }


    private static void drawDebugBounds(GuiGraphicsExtractor graphics, DAI_OverlayLayer layer) {
        int left = layer.left(graphics.guiWidth());
        int top = layer.top(graphics.guiHeight());
        int right = left + layer.width();
        int bottom = top + layer.height();
        boolean hovered = layer.boundsContain(
                io.github.j12h36h.dai.client.logics.input.DAI_MouseState.x(),
                io.github.j12h36h.dai.client.logics.input.DAI_MouseState.y(),
                graphics.guiWidth(),
                graphics.guiHeight()
        );
        int color = hovered ? 0xFFFF8428 : 0xBBA855F7;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    public static Identifier textureIdentifier(String authored) {
        Identifier id = Identifier.tryParse(authored == null ? "" : authored.trim());
        if (id == null) {
            return null;
        }

        String path = id.getPath();
        if (!path.startsWith("textures/")) {
            path = "textures/" + path;
        }
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }

        return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    public static int tint(String color, double alpha) {
        int rgb = 0xFFFFFF;
        int colorAlpha = 0xFF;
        String normalized = color == null ? "" : color.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        try {
            if (normalized.length() == 6) {
                rgb = Integer.parseUnsignedInt(normalized, 16) & 0xFFFFFF;
            } else if (normalized.length() == 8) {
                long argb = Long.parseLong(normalized, 16);
                colorAlpha = (int) ((argb >>> 24) & 0xFF);
                rgb = (int) argb & 0xFFFFFF;
            }
        } catch (NumberFormatException ignored) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid overlay tint '{}'; using white.",
                    color
            );
        }

        double layerAlpha = Math.max(0.1D, Math.min(1.0D, alpha));
        double globalAlpha = Math.max(0.25D, Math.min(1.0D, DAI_Config.overlayOpacity()));
        int a = (int) Math.round(layerAlpha * globalAlpha * colorAlpha);
        return (a << 24) | rgb;
    }
}
