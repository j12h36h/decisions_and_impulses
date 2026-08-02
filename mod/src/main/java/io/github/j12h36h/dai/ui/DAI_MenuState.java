package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.gui.components.Button;

import java.util.Arrays;
import java.util.EnumMap;

public final class DAI_MenuState {

    public static final int SUBMENU_SLOT_COUNT = 3;

    public enum SystemMode {
        DATAPACK,
        QUEUE
    }

    private final EnumMap<DAI_MenuCategory, Button> rootButtons =
            new EnumMap<>(DAI_MenuCategory.class);

    private final EnumMap<DAI_MenuCategory, Button[]> subButtons =
            new EnumMap<>(DAI_MenuCategory.class);

    private final EnumMap<DAI_MenuCategory, Boolean> menuOpen =
            new EnumMap<>(DAI_MenuCategory.class);

    private SystemMode systemMode =
            SystemMode.DATAPACK;

    public DAI_MenuState() {

        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {

            subButtons.put(
                    category,
                    new Button[SUBMENU_SLOT_COUNT]
            );

            menuOpen.put(
                    category,
                    false
            );
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Menu state initialized."
        );
    }

    public SystemMode systemMode() {
        return systemMode;
    }

    public void setSystemMode(
            SystemMode systemMode
    ) {

        if (systemMode == null) {

            throw new IllegalArgumentException(
                    "System menu mode cannot be null."
            );
        }

        if (this.systemMode == systemMode) {
            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: System menu mode changed from {} to {}.",
                this.systemMode,
                systemMode
        );

        this.systemMode = systemMode;
    }

    public Button rootButton(
            DAI_MenuCategory category
    ) {

        return rootButtons.get(
                requireCategory(category)
        );
    }

    public void setRootButton(
            DAI_MenuCategory category,
            Button button
    ) {

        requireCategory(category);

        if (button == null) {

            throw new IllegalArgumentException(
                    "Root menu button cannot be null."
            );
        }

        rootButtons.put(
                category,
                button
        );
    }

    public Button subButton(
            DAI_MenuCategory category,
            int slot
    ) {

        validateSlot(slot);

        return subButtons
                .get(requireCategory(category))[slot];
    }

    public void setSubButton(
            DAI_MenuCategory category,
            int slot,
            Button button
    ) {

        validateSlot(slot);

        subButtons
                .get(requireCategory(category))[slot] = button;
    }

    public Button removeSubButton(
            DAI_MenuCategory category,
            int slot
    ) {

        validateSlot(slot);

        Button[] buttons =
                subButtons.get(requireCategory(category));

        Button button = buttons[slot];

        buttons[slot] = null;

        return button;
    }

    public Button[] subButtons(
            DAI_MenuCategory category
    ) {

        return subButtons.get(
                requireCategory(category)
        );
    }

    public boolean isOpen(
            DAI_MenuCategory category
    ) {

        return Boolean.TRUE.equals(
                menuOpen.get(requireCategory(category))
        );
    }

    public void setOpen(
            DAI_MenuCategory category,
            boolean open
    ) {

        requireCategory(category);

        boolean previous =
                Boolean.TRUE.equals(menuOpen.get(category));

        if (previous == open) {
            return;
        }

        menuOpen.put(
                category,
                open
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: {} menu {}.",
                category,
                open ? "opened" : "closed"
        );
    }

    public void reset() {

        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {

            rootButtons.remove(category);

            Arrays.fill(
                    subButtons.get(category),
                    null
            );

            menuOpen.put(
                    category,
                    false
            );
        }

        systemMode = SystemMode.DATAPACK;

        DAI_Core.LOGGER.debug(
                "<DAI>: Menu state reset."
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

    private static void validateSlot(
            int slot
    ) {

        if (slot < 0 || slot >= SUBMENU_SLOT_COUNT) {

            throw new IndexOutOfBoundsException(
                    "Menu slot must be between 0 and "
                            + (SUBMENU_SLOT_COUNT - 1)
                            + ": "
                            + slot
            );
        }
    }
}
