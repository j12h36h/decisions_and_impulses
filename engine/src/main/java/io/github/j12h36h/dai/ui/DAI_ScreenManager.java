package io.github.j12h36h.dai.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DAI_ScreenManager {

    private static final Deque<Screen> SCREEN_STACK = new ArrayDeque<>();

    private DAI_ScreenManager() {
    }


    public static void push(Screen screen) {

        if (screen != null) {
            SCREEN_STACK.push(screen);
        }
    }


    public static void open(Screen screen) {

        Minecraft.getInstance()
                .gui
                .setScreen(screen);
    }


    public static Screen pop() {

        return SCREEN_STACK.isEmpty()
                ? null
                : SCREEN_STACK.pop();
    }


    public static Screen peek() {

        return SCREEN_STACK.peek();
    }


    public static boolean isEmpty() {

        return SCREEN_STACK.isEmpty();
    }


    public static void clear() {

        SCREEN_STACK.clear();
    }
}