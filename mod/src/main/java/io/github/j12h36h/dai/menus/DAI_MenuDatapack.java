package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.menus.system.DAI_SystemButton;
import io.github.j12h36h.dai.menus.system.DAI_SystemDefinition;
import io.github.j12h36h.dai.menus.system.DAI_SystemManager;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class DAI_MenuDatapack {

    public DAI_MenuDatapack() {
        // Instance belongs to one menu screen.
    }

    public void open(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            DAI_MenuCategory category,
            String id
    ) {

        requireMenu(menu);
        requireState(state);
        requireLayout(layout);
        requireCategory(category);

        if (id == null || id.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open {} datapack menu with an empty id.",
                    category
            );

            return;
        }

        String normalizedId = id.trim();

        DAI_SystemDefinition definition =
                DAI_SystemManager.get(
                        category,
                        normalizedId
                );

        if (definition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown {} menu '{}'.",
                    category,
                    normalizedId
            );

            return;
        }

        menu.closeMenu(category);

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening {} datapack menu '{}' with {} button(s).",
                category,
                normalizedId,
                definition.buttons().size()
        );

        state.setOpen(
                category,
                true
        );

        for (DAI_SystemButton buttonDefinition : definition.buttons()) {

            createButton(
                    menu,
                    state,
                    layout,
                    category,
                    normalizedId,
                    buttonDefinition
            );
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Opened {} datapack menu '{}'.",
                category,
                normalizedId
        );
    }

    private void createButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            DAI_MenuCategory category,
            String definitionId,
            DAI_SystemButton definition
    ) {

        if (definition == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Null button found in {} menu '{}'.",
                    category,
                    definitionId
            );

            return;
        }

        int slot = definition.slot();

        if (
                slot < 0
                        || slot >= DAI_MenuState.SUBMENU_SLOT_COUNT
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid slot {} in {} menu '{}'.",
                    slot,
                    category,
                    definitionId
            );

            return;
        }

        Button existingButton =
                state.subButton(
                        category,
                        slot
                );

        if (existingButton != null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Replacing occupied slot {} in {} menu '{}'.",
                    slot,
                    category,
                    definitionId
            );

            menu.removeMenuWidget(existingButton);
        }

        DAI_Layout.Layout buttonLayout =
                layout.sub(
                        category,
                        slot
                );

        Button button =
                Button.builder(
                                Component.literal(
                                        definition.text()
                                ),
                                pressedButton ->
                                        menu.runAction(
                                                definition.action()
                                        )
                        )
                        .bounds(
                                buttonLayout.x(),
                                buttonLayout.y(),
                                buttonLayout.width(),
                                buttonLayout.height()
                        )
                        .build();

        state.setSubButton(
                category,
                slot,
                button
        );

        menu.addMenuWidget(button);

        DAI_Core.LOGGER.debug(
                "<DAI>: Added {} menu button '{}' to slot {} with action '{}'.",
                category,
                definition.id(),
                slot,
                definition.action()
        );
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
