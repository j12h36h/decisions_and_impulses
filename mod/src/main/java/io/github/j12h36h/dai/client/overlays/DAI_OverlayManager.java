package io.github.j12h36h.dai.client.overlays;

import io.github.j12h36h.dai.client.experience.DAI_ExperienceRuntime;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
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
        int a = (int) Math.round(layerAlpha * colorAlpha);
        return (a << 24) | rgb;
    }
}
