package io.github.j12h36h.dai.client.logics.condition;

import io.github.j12h36h.dai.client.logics.input.DAI_MouseState;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayLayer;
import io.github.j12h36h.dai.client.overlays.DAI_OverlayManager;

/** Datapack-readable mouse and overlay geometry conditions. */
public final class DAI_ConditionsUi {

    private DAI_ConditionsUi() {
        // Utility class.
    }

    public static void registerAll() {
        DAI_ConditionRegistry.register("mouse_x", (context, condition) -> DAI_ConditionValue.number(DAI_MouseState.x()));
        DAI_ConditionRegistry.register("mouse_y", (context, condition) -> DAI_ConditionValue.number(DAI_MouseState.y()));
        DAI_ConditionRegistry.register("mouse_delta_x", (context, condition) -> DAI_ConditionValue.number(DAI_MouseState.deltaX()));
        DAI_ConditionRegistry.register("mouse_delta_y", (context, condition) -> DAI_ConditionValue.number(DAI_MouseState.deltaY()));

        DAI_ConditionRegistry.register("mouse_button_held", (context, condition) ->
                button(condition.parameter(), DAI_MouseState::isHeld));
        DAI_ConditionRegistry.register("mouse_button_pressed", (context, condition) ->
                button(condition.parameter(), DAI_MouseState::wasPressed));
        DAI_ConditionRegistry.register("mouse_button_released", (context, condition) ->
                button(condition.parameter(), DAI_MouseState::wasReleased));

        DAI_ConditionRegistry.register("overlay_exists", (context, condition) ->
                DAI_ConditionValue.bool(DAI_OverlayManager.contains(condition.parameter())));
        DAI_ConditionRegistry.register("mouse_over_overlay", (context, condition) -> {
            DAI_OverlayLayer layer = DAI_OverlayManager.get(condition.parameter());
            if (layer == null || DAI_OverlayManager.guiWidth() <= 0 || DAI_OverlayManager.guiHeight() <= 0) {
                return DAI_ConditionValue.missing();
            }
            return DAI_ConditionValue.bool(layer.boundsContain(
                    DAI_MouseState.x(), DAI_MouseState.y(),
                    DAI_OverlayManager.guiWidth(), DAI_OverlayManager.guiHeight()));
        });
        DAI_ConditionRegistry.register("mouse_near_overlay", (context, condition) -> {
            DAI_OverlayLayer layer = DAI_OverlayManager.get(condition.parameter());
            if (layer == null || DAI_OverlayManager.guiWidth() <= 0 || DAI_OverlayManager.guiHeight() <= 0) {
                return DAI_ConditionValue.missing();
            }
            double threshold = condition.parameterNumber() <= 0.0D ? 24.0D : condition.parameterNumber();
            return DAI_ConditionValue.bool(layer.distanceTo(
                    DAI_MouseState.x(), DAI_MouseState.y(),
                    DAI_OverlayManager.guiWidth(), DAI_OverlayManager.guiHeight()) <= threshold);
        });
        DAI_ConditionRegistry.register("mouse_distance_to_overlay", (context, condition) -> {
            DAI_OverlayLayer layer = DAI_OverlayManager.get(condition.parameter());
            if (layer == null || DAI_OverlayManager.guiWidth() <= 0 || DAI_OverlayManager.guiHeight() <= 0) {
                return DAI_ConditionValue.missing();
            }
            return DAI_ConditionValue.number(layer.distanceTo(
                    DAI_MouseState.x(), DAI_MouseState.y(),
                    DAI_OverlayManager.guiWidth(), DAI_OverlayManager.guiHeight()));
        });

        DAI_ConditionRegistry.register("gui_width", (context, condition) -> DAI_ConditionValue.number(DAI_OverlayManager.guiWidth()));
        DAI_ConditionRegistry.register("gui_height", (context, condition) -> DAI_ConditionValue.number(DAI_OverlayManager.guiHeight()));

        registerOverlayNumber("overlay_x", layer -> layer.offsetX());
        registerOverlayNumber("overlay_y", layer -> layer.offsetY());
        registerOverlayNumber("overlay_width", layer -> layer.width());
        registerOverlayNumber("overlay_height", layer -> layer.height());
        registerOverlayNumber("overlay_z", layer -> layer.z());
        registerOverlayNumber("overlay_center_x", layer -> layer.centerX(DAI_OverlayManager.guiWidth()));
        registerOverlayNumber("overlay_center_y", layer -> layer.centerY(DAI_OverlayManager.guiHeight()));

        DAI_ConditionRegistry.register("overlay_transform_locked", (context, condition) -> {
            DAI_OverlayLayer layer = DAI_OverlayManager.get(condition.parameter());
            return layer == null ? DAI_ConditionValue.missing() : DAI_ConditionValue.bool(layer.transformLocked());
        });
    }

    private static void registerOverlayNumber(String id, OverlayNumberReader reader) {
        DAI_ConditionRegistry.register(id, (context, condition) -> {
            DAI_OverlayLayer layer = DAI_OverlayManager.get(condition.parameter());
            if (layer == null) return DAI_ConditionValue.missing();
            return DAI_ConditionValue.number(reader.read(layer));
        });
    }

    private static DAI_ConditionValue button(String id, ButtonReader reader) {
        if (DAI_MouseState.buttonIndex(id) < 0) return DAI_ConditionValue.missing();
        return DAI_ConditionValue.bool(reader.read(id));
    }

    @FunctionalInterface
    private interface ButtonReader { boolean read(String id); }

    @FunctionalInterface
    private interface OverlayNumberReader { double read(DAI_OverlayLayer layer); }
}
