package io.github.j12h36h.dai.input;

public final class DAI_AutomationController {

    private static int selectedIndex;

    private DAI_AutomationController() {
        // Utility class.
    }

    public static int selectedIndex() {
        return selectedIndex;
    }

    public static void previous(
            int size
    ) {

        if (size <= 0) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex - 1,
                        size
                );
    }

    public static void next(
            int size
    ) {

        if (size <= 0) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex + 1,
                        size
                );
    }

    public static void reset() {
        selectedIndex = 0;
    }
}
