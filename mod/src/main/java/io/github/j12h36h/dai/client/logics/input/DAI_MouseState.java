package io.github.j12h36h.dai.client.logics.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Tick-stable mouse snapshot for datapack conditions, overlays and diagnostics.
 * Button edges are captured by Mixin_Mouse and cleared at the beginning of the
 * next gameplay tick, matching DAI_KeybindStateTracker semantics.
 */
public final class DAI_MouseState {

    private static double x;
    private static double y;
    private static double previousX;
    private static double previousY;

    private static final boolean[] HELD = new boolean[8];
    private static final boolean[] PRESSED = new boolean[8];
    private static final boolean[] RELEASED = new boolean[8];

    private DAI_MouseState() {
        // Utility class.
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.mouseHandler == null || minecraft.getWindow() == null) {
            return;
        }

        previousX = x;
        previousY = y;
        x = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        y = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
    }

    public static void onButton(int button, int action) {
        if (button < 0 || button >= HELD.length) {
            return;
        }

        if (action == GLFW.GLFW_PRESS) {
            if (!HELD[button]) {
                PRESSED[button] = true;
            }
            HELD[button] = true;
        } else if (action == GLFW.GLFW_RELEASE) {
            if (HELD[button]) {
                RELEASED[button] = true;
            }
            HELD[button] = false;
        }
    }

    public static double x() { return x; }
    public static double y() { return y; }
    public static double deltaX() { return x - previousX; }
    public static double deltaY() { return y - previousY; }

    public static boolean isHeld(String button) {
        int index = buttonIndex(button);
        return index >= 0 && HELD[index];
    }

    public static boolean wasPressed(String button) {
        int index = buttonIndex(button);
        return index >= 0 && PRESSED[index];
    }

    public static boolean wasReleased(String button) {
        int index = buttonIndex(button);
        return index >= 0 && RELEASED[index];
    }

    public static int buttonIndex(String button) {
        if (button == null) return -1;
        return switch (button.trim().toLowerCase()) {
            case "left", "mouse_left", "0" -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case "right", "mouse_right", "1" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case "middle", "mouse_middle", "2" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> {
                try {
                    int parsed = Integer.parseInt(button.trim());
                    yield parsed >= 0 && parsed < HELD.length ? parsed : -1;
                } catch (NumberFormatException ignored) {
                    yield -1;
                }
            }
        };
    }

    public static void finishTick() {
        clearEdges();
    }

    public static void reset() {
        x = 0.0D;
        y = 0.0D;
        previousX = 0.0D;
        previousY = 0.0D;
        for (int i = 0; i < HELD.length; i++) {
            HELD[i] = false;
            PRESSED[i] = false;
            RELEASED[i] = false;
        }
    }

    private static void clearEdges() {
        for (int i = 0; i < PRESSED.length; i++) {
            PRESSED[i] = false;
            RELEASED[i] = false;
        }
    }
}
