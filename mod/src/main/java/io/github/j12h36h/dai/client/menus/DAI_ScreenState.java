package io.github.j12h36h.dai.client.menus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.AbstractContainerMenu;

public final class DAI_ScreenState {

    private DAI_ScreenState() {
        // Utility class.
    }

    /*
     * ------------------------------------------------------------
     * SCREEN
     * ------------------------------------------------------------
     */

    public static boolean hasScreen() {

        return screen() != null;
    }

    public static Screen screen() {

        Minecraft minecraft =
                Minecraft.getInstance();

        return minecraft.gui.screen();
    }

    public static String screenClass() {

        Screen screen =
                screen();

        if (screen == null) {
            return "";
        }

        return screen.getClass()
                .getName();
    }

    public static String screenSimpleClass() {

        Screen screen =
                screen();

        if (screen == null) {
            return "";
        }

        return screen.getClass()
                .getSimpleName();
    }

    public static String screenTitle() {

        Screen screen =
                screen();

        if (screen == null) {
            return "";
        }

        return screen.getTitle()
                .getString();
    }

    /*
     * ------------------------------------------------------------
     * SCREEN DIMENSIONS
     * ------------------------------------------------------------
     */

    public static int screenWidth() {

        Screen screen =
                screen();

        if (screen == null) {
            return 0;
        }

        return screen.width;
    }

    public static int screenHeight() {

        Screen screen =
                screen();

        if (screen == null) {
            return 0;
        }

        return screen.height;
    }

    /*
     * ------------------------------------------------------------
     * CONTAINER MENU
     * ------------------------------------------------------------
     */

    public static boolean hasMenu() {

        return menu() != null;
    }

    public static AbstractContainerMenu menu() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return null;
        }

        return minecraft.player.containerMenu;
    }

    public static String menuClass() {

        AbstractContainerMenu menu =
                menu();

        if (menu == null) {
            return "";
        }

        return menu.getClass()
                .getName();
    }

    public static String menuSimpleClass() {

        AbstractContainerMenu menu =
                menu();

        if (menu == null) {
            return "";
        }

        return menu.getClass()
                .getSimpleName();
    }

    public static int containerId() {

        AbstractContainerMenu menu =
                menu();

        if (menu == null) {
            return -1;
        }

        return menu.containerId;
    }

    /*
     * ------------------------------------------------------------
     * SLOTS
     * ------------------------------------------------------------
     */

    public static int slotCount() {

        AbstractContainerMenu menu =
                menu();

        if (menu == null) {
            return 0;
        }

        return menu.slots.size();
    }

    public static boolean hasSlot(
            int slot
    ) {

        AbstractContainerMenu menu =
                menu();

        return menu != null
                && slot >= 0
                && slot < menu.slots.size();
    }

    /*
     * ------------------------------------------------------------
     * STATE
     * ------------------------------------------------------------
     */

    /**
     * Returns true when an actual GUI screen is open and the player has
     * an associated container menu.
     *
     * The player's normal InventoryMenu exists even when no GUI is open,
     * so checking the menu alone is not enough to determine whether DAI
     * is currently interacting with a screen.
     */
    public static boolean hasOpenContainerScreen() {

        return hasScreen()
                && hasMenu();
    }

    /**
     * Convenience matcher for datapack-driven screen profiles.
     */
    public static boolean screenIs(
            String className
    ) {

        if (
                className == null
                        || className.isBlank()
        ) {
            return false;
        }

        String expected =
                className.trim();

        return expected.equals(
                screenClass()
        )
                || expected.equals(
                screenSimpleClass()
        );
    }

    /**
     * Convenience matcher for datapack-driven menu profiles.
     */
    public static boolean menuIs(
            String className
    ) {

        if (
                className == null
                        || className.isBlank()
        ) {
            return false;
        }

        String expected =
                className.trim();

        return expected.equals(
                menuClass()
        )
                || expected.equals(
                menuSimpleClass()
        );
    }
}