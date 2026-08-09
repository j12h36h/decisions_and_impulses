package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Config;
import io.github.j12h36h.dai.logics.core.DAI_Core;

import java.util.EnumMap;

public final class DAI_MenuLayout {

    public static final int DEFAULT_WIDTH = 150;
    public static final int DEFAULT_HEIGHT = 20;

    private final EnumMap<
            DAI_MenuCategory,
            DAI_Layout.Layout
            > layouts =
            new EnumMap<>(DAI_MenuCategory.class);

    public DAI_MenuLayout() {
        // Instance state belongs to one menu screen.
    }

    public void initialize(
            int screenWidth,
            int screenHeight
    ) {

        layouts.clear();

        register(
                DAI_MenuCategory.SYSTEM,
                DAI_Config.SYSTEM_MENU_POSITION.get(),
                screenWidth,
                screenHeight
        );

        register(
                DAI_MenuCategory.ACTION,
                DAI_Config.ACTION_MENU_POSITION.get(),
                screenWidth,
                screenHeight
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Menu layouts initialized for screen {}x{}.",
                screenWidth,
                screenHeight
        );
    }

    public DAI_Layout.Layout root(
            DAI_MenuCategory category
    ) {

        DAI_Layout.Layout layout =
                layouts.get(requireCategory(category));

        if (layout == null) {

            throw new IllegalStateException(
                    "Menu layout has not been initialized for category: "
                            + category
            );
        }

        return layout;
    }

    public DAI_Layout.Layout sub(
            DAI_MenuCategory category,
            int slot
    ) {

        if (
                slot < 0
                        || slot >= DAI_MenuState.SUBMENU_SLOT_COUNT
        ) {

            throw new IndexOutOfBoundsException(
                    "Menu slot must be between 0 and "
                            + (DAI_MenuState.SUBMENU_SLOT_COUNT - 1)
                            + ": "
                            + slot
            );
        }

        return DAI_Layout.getSubLayout(
                root(category),
                slot
        );
    }

    public boolean contains(
            DAI_MenuCategory category
    ) {

        return layouts.containsKey(
                requireCategory(category)
        );
    }

    public void clear() {

        int removed = layouts.size();

        layouts.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared {} menu layout(s).",
                removed
        );
    }

    private void register(
            DAI_MenuCategory category,
            DAI_Position position,
            int screenWidth,
            int screenHeight
    ) {

        if (position == null) {

            throw new IllegalArgumentException(
                    "Menu position cannot be null for category: "
                            + category
            );
        }

        DAI_Layout.Layout layout =
                DAI_Layout.getLayout(
                        position,
                        screenWidth,
                        screenHeight,
                        DEFAULT_WIDTH,
                        DEFAULT_HEIGHT,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN
                );

        layouts.put(
                category,
                layout
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Registered {} menu layout at {}: x={}, y={}, width={}, height={}.",
                category,
                position,
                layout.x(),
                layout.y(),
                layout.width(),
                layout.height()
        );
    }

    private static DAI_MenuCategory requireCategory(
            DAI_MenuCategory category
    ) {

        if (category == null) {

            throw new IllegalArgumentException(
                    "Menu category cannot be null."
            );
        }

        return category;
    }
}