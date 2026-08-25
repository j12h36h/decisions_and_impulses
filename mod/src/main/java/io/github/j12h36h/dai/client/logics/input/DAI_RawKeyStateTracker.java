package io.github.j12h36h.dai.client.logics.input;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Physical keyboard state independent of Minecraft key mappings.
 *
 * Important: GLFW rejects arbitrary integers that are not declared keyboard
 * constants. Never sweep 0..GLFW_KEY_LAST. We poll only the key constants DAI
 * actually exposes to datapacks. This also avoids polluting Minecraft's GL
 * error log with "Invalid key" on every frame.
 */
public final class DAI_RawKeyStateTracker {

    private static final boolean[] LAST = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] DOWN = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] PRESSED = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] RELEASED = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private DAI_RawKeyStateTracker() {}

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            reset();
            return;
        }

        long window = minecraft.getWindow().handle();

        for (int key = 0; key <= GLFW.GLFW_KEY_LAST; key++) {
            if (!isSupportedKeyCode(key)) {
                continue;
            }

            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            DOWN[key] = down;
            PRESSED[key] = down && !LAST[key];
            RELEASED[key] = !down && LAST[key];
            LAST[key] = down;
        }
    }

    public static boolean exists(String id) {
        return keyCode(id) >= 0;
    }

    public static boolean isHeld(String id) {
        int key = keyCode(id);
        return key >= 0 && DOWN[key];
    }

    public static boolean wasPressed(String id) {
        int key = keyCode(id);
        return key >= 0 && PRESSED[key];
    }

    public static boolean wasReleased(String id) {
        int key = keyCode(id);
        return key >= 0 && RELEASED[key];
    }

    public static void reset() {
        java.util.Arrays.fill(LAST, false);
        java.util.Arrays.fill(DOWN, false);
        java.util.Arrays.fill(PRESSED, false);
        java.util.Arrays.fill(RELEASED, false);
    }

    private static boolean isSupportedKeyCode(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) return true;
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return true;
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) return true;

        return switch (key) {
            case GLFW.GLFW_KEY_SPACE,
                 GLFW.GLFW_KEY_ESCAPE,
                 GLFW.GLFW_KEY_ENTER,
                 GLFW.GLFW_KEY_TAB,
                 GLFW.GLFW_KEY_BACKSPACE,
                 GLFW.GLFW_KEY_INSERT,
                 GLFW.GLFW_KEY_DELETE,
                 GLFW.GLFW_KEY_RIGHT,
                 GLFW.GLFW_KEY_LEFT,
                 GLFW.GLFW_KEY_DOWN,
                 GLFW.GLFW_KEY_UP,
                 GLFW.GLFW_KEY_PAGE_UP,
                 GLFW.GLFW_KEY_PAGE_DOWN,
                 GLFW.GLFW_KEY_HOME,
                 GLFW.GLFW_KEY_END,
                 GLFW.GLFW_KEY_CAPS_LOCK,
                 GLFW.GLFW_KEY_SCROLL_LOCK,
                 GLFW.GLFW_KEY_NUM_LOCK,
                 GLFW.GLFW_KEY_PRINT_SCREEN,
                 GLFW.GLFW_KEY_PAUSE,
                 GLFW.GLFW_KEY_LEFT_SHIFT,
                 GLFW.GLFW_KEY_LEFT_CONTROL,
                 GLFW.GLFW_KEY_LEFT_ALT,
                 GLFW.GLFW_KEY_RIGHT_SHIFT,
                 GLFW.GLFW_KEY_RIGHT_CONTROL,
                 GLFW.GLFW_KEY_RIGHT_ALT -> true;
            default -> false;
        };
    }

    private static int keyCode(String raw) {
        if (raw == null) return -1;
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("key.keyboard.")) id = id.substring("key.keyboard.".length());

        if (id.length() == 1) {
            char c = id.charAt(0);
            if (c >= 'a' && c <= 'z') return GLFW.GLFW_KEY_A + (c - 'a');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }

        return switch (id) {
            case "space" -> GLFW.GLFW_KEY_SPACE;
            case "escape", "esc" -> GLFW.GLFW_KEY_ESCAPE;
            case "enter", "return" -> GLFW.GLFW_KEY_ENTER;
            case "tab" -> GLFW.GLFW_KEY_TAB;
            case "backspace" -> GLFW.GLFW_KEY_BACKSPACE;
            case "insert" -> GLFW.GLFW_KEY_INSERT;
            case "delete" -> GLFW.GLFW_KEY_DELETE;
            case "right", "right_arrow" -> GLFW.GLFW_KEY_RIGHT;
            case "left", "left_arrow" -> GLFW.GLFW_KEY_LEFT;
            case "down", "down_arrow" -> GLFW.GLFW_KEY_DOWN;
            case "up", "up_arrow" -> GLFW.GLFW_KEY_UP;
            case "page_up" -> GLFW.GLFW_KEY_PAGE_UP;
            case "page_down" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "home" -> GLFW.GLFW_KEY_HOME;
            case "end" -> GLFW.GLFW_KEY_END;
            case "caps_lock" -> GLFW.GLFW_KEY_CAPS_LOCK;
            case "scroll_lock" -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case "num_lock" -> GLFW.GLFW_KEY_NUM_LOCK;
            case "print_screen" -> GLFW.GLFW_KEY_PRINT_SCREEN;
            case "pause" -> GLFW.GLFW_KEY_PAUSE;
            case "left_shift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "left_control", "left_ctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "left_alt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "right_shift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "right_control", "right_ctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "right_alt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            default -> functionKey(id);
        };
    }

    private static int functionKey(String id) {
        if (id.length() >= 2 && id.charAt(0) == 'f') {
            try {
                int number = Integer.parseInt(id.substring(1));
                if (number >= 1 && number <= 25) return GLFW.GLFW_KEY_F1 + number - 1;
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}
