package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_SystemButton;
import io.github.j12h36h.dai.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.menus.system.DAI_SystemManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class DAI_MenuAutomation {

    private static final String DEFINITION_ID =
            "automation";

    private int selectedIndex;

    public DAI_MenuAutomation() {
        // Instance belongs to one menu screen.
    }

    public void open(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout
    ) {

        requireMenu(menu);
        requireState(state);
        requireLayout(layout);

        DAI_MenuCategory category =
                DAI_MenuCategory.ACTION;

        menu.closeMenu(category);

        state.setActionMode(
                DAI_MenuState.ActionMode.AUTOMATION
        );

        state.setOpen(
                category,
                true
        );

        normalizeSelection();

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening automation selector menu."
        );

        DAI_MenuSelector.create(
                menu,
                state,
                layout,
                category,
                button -> {

                    previous();

                    refresh(state);
                },
                button -> {

                    DAI_SystemButton selected =
                            selected();

                    if (selected == null) {

                        DAI_Core.LOGGER.debug(
                                "<DAI>: Automation selection ignored because no entries are available."
                        );

                        return;
                    }

                    DAI_Core.LOGGER.debug(
                            "<DAI>: Opening selected automation '{}' with action '{}'.",
                            selected.id(),
                            selected.action()
                    );

                    menu.runAction(
                            selected.action()
                    );
                },
                button -> {

                    next();

                    refresh(state);
                }
        );

        refresh(state);

        DAI_Core.LOGGER.debug(
                "<DAI>: Automation selector menu opened with {} entry(s).",
                entries().size()
        );
    }

    public void refresh(
            DAI_MenuState state
    ) {

        requireState(state);

        Button selectedButton =
                state.subButton(
                        DAI_MenuCategory.ACTION,
                        1
                );

        if (selectedButton == null) {
            return;
        }

        List<DAI_SystemButton> entries =
                entries();

        DAI_SystemButton selected =
                selected(entries);

        if (selected == null) {

            selectedButton.setMessage(
                    Component.literal("[ Empty ]")
            );

            return;
        }

        selectedButton.setMessage(
                Component.literal(
                        String.format(
                                Locale.ROOT,
                                "{%d/%d} %s",
                                selectedIndex + 1,
                                entries.size(),
                                selected.text()
                        )
                )
        );
    }

    private void previous() {

        List<DAI_SystemButton> entries =
                entries();

        if (entries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex - 1,
                        entries.size()
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected previous automation entry at index {}.",
                selectedIndex
        );
    }

    private void next() {

        List<DAI_SystemButton> entries =
                entries();

        if (entries.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex + 1,
                        entries.size()
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Selected next automation entry at index {}.",
                selectedIndex
        );
    }

    private DAI_SystemButton selected() {

        return selected(
                entries()
        );
    }

    private DAI_SystemButton selected(
            List<DAI_SystemButton> entries
    ) {

        if (entries.isEmpty()) {
            return null;
        }

        normalizeSelection(
                entries.size()
        );

        return entries.get(
                selectedIndex
        );
    }

    private void normalizeSelection() {

        normalizeSelection(
                entries().size()
        );
    }

    private void normalizeSelection(
            int entryCount
    ) {

        if (entryCount <= 0) {
            selectedIndex = 0;
            return;
        }

        selectedIndex =
                Math.floorMod(
                        selectedIndex,
                        entryCount
                );
    }

    private static List<DAI_SystemButton> entries() {

        DAI_SystemDefinition definition =
                DAI_SystemManager.get(
                        DAI_MenuCategory.ACTION,
                        DEFINITION_ID
                );

        if (definition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Automation definition '{}' was not found.",
                    DEFINITION_ID
            );

            return List.of();
        }

        return definition.buttons()
                .stream()
                .sorted(
                        Comparator.comparingInt(
                                DAI_SystemButton::slot
                        )
                )
                .toList();
    }

    private static DAI_MenuCore requireMenu(
            DAI_MenuCore menu
    ) {

        if (menu == null) {

            throw new IllegalArgumentException(
                    "Menu core cannot be null."
            );
        }

        return menu;
    }

    private static DAI_MenuState requireState(
            DAI_MenuState state
    ) {

        if (state == null) {

            throw new IllegalArgumentException(
                    "Menu state cannot be null."
            );
        }

        return state;
    }

    private static DAI_MenuLayout requireLayout(
            DAI_MenuLayout layout
    ) {

        if (layout == null) {

            throw new IllegalArgumentException(
                    "Menu layout cannot be null."
            );
        }

        return layout;
    }
}