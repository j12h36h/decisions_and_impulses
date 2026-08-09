package io.github.j12h36h.dai.menus;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class DAI_MenuSelector {

    private DAI_MenuSelector() {
        // Utility class.
    }

    public static void create(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            DAI_MenuCategory category,
            Button.OnPress previousAction,
            Button.OnPress selectedAction,
            Button.OnPress nextAction
    ) {

        requireMenu(menu);
        requireState(state);
        requireLayout(layout);
        requireCategory(category);
        requireAction(previousAction);
        requireAction(selectedAction);
        requireAction(nextAction);

        createButton(
                menu,
                state,
                layout,
                category,
                0,
                "▲",
                previousAction
        );

        createButton(
                menu,
                state,
                layout,
                category,
                1,
                "",
                selectedAction
        );

        createButton(
                menu,
                state,
                layout,
                category,
                2,
                "▼",
                nextAction
        );
    }

    private static void createButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            DAI_MenuCategory category,
            int slot,
            String text,
            Button.OnPress action
    ) {

        Button existingButton =
                state.subButton(
                        category,
                        slot
                );

        if (existingButton != null) {
            menu.removeMenuWidget(existingButton);
        }

        DAI_Layout.Layout buttonLayout =
                layout.sub(
                        category,
                        slot
                );

        Button button =
                Button.builder(
                                Component.literal(text),
                                action
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

    private static Button.OnPress requireAction(
            Button.OnPress action
    ) {

        if (action == null) {

            throw new IllegalArgumentException(
                    "Menu selector action cannot be null."
            );
        }

        return action;
    }
}