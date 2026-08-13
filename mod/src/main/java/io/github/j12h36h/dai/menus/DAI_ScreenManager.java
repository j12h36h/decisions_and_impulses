package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DAI_ScreenManager {

    private static final Deque<Screen> SCREEN_STACK = new ArrayDeque<>();

    private DAI_ScreenManager() {
        // Utility class.
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null
                || minecraft.level == null
                || minecraft.gui.screen() != null) {
            return;
        }

        Screen screen = SCREEN_STACK.poll();
        if (screen == null) {
            return;
        }

        DAI_Core.debug(
                "<DAI>: Restoring screen {}.",
                screen.getClass().getSimpleName()
        );
        minecraft.gui.setScreen(screen);
    }

    public static void openTemporary(Screen current, Screen next) {
        if (next == null) {
            DAI_Core.LOGGER.warn("<DAI>: Attempted to open a null temporary screen.");
            return;
        }

        if (current != null && SCREEN_STACK.peek() != current) {
            SCREEN_STACK.push(current);
        }

        open(next);
    }

    public static void push(Screen screen) {
        if (screen != null && SCREEN_STACK.peek() != screen) {
            SCREEN_STACK.push(screen);
        }
    }

    public static void open(Screen screen) {
        if (screen == null) {
            DAI_Core.LOGGER.warn("<DAI>: Attempted to open a null screen.");
            return;
        }
        Minecraft.getInstance().gui.setScreen(screen);
    }

    public static Screen pop() {
        return SCREEN_STACK.poll();
    }

    public static Screen peek() {
        return SCREEN_STACK.peek();
    }

    public static void clear() {
        SCREEN_STACK.clear();
    }
}
