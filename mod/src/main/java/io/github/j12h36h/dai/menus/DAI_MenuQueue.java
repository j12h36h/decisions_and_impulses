package io.github.j12h36h.dai.menus;

import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class DAI_MenuQueue {

    public DAI_MenuQueue() {
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
                DAI_MenuCategory.SYSTEM;

        menu.closeMenu(category);

        state.setSystemMode(
                DAI_MenuState.SystemMode.QUEUE
        );

        state.setOpen(
                category,
                true
        );

        DAI_MenuSelector.create(
                menu,
                state,
                layout,
                category,
                button -> {
                    DAI_ActionQueue.previous();
                    refresh(state);
                },
                button -> {
                    int selectedIndex =
                            DAI_ActionQueue.selectedIndex();

                    if (DAI_ActionQueue.selected() == null) {
                        return;
                    }

                    DAI_ActionQueue.remove(
                            selectedIndex
                    );

                    refresh(state);
                },
                button -> {
                    DAI_ActionQueue.next();
                    refresh(state);
                }
        );

        refresh(state);
    }

    public void refresh(
            DAI_MenuState state
    ) {

        requireState(state);

        Button selectedButton =
                state.subButton(
                        DAI_MenuCategory.SYSTEM,
                        1
                );

        if (selectedButton == null) {
            return;
        }

        DAI_ActionDefinition action =
                DAI_ActionQueue.selected();

        if (action == null) {

            selectedButton.setMessage(
                    Component.literal("[ Empty ]")
            );

            return;
        }

        double seconds =
                DAI_ActionQueue.delayTicks() / 20.0D;

        selectedButton.setMessage(
                Component.literal(
                        String.format(
                                Locale.ROOT,
                                "{%d} %s (%.1fs)",
                                DAI_ActionQueue.selectedIndex() + 1,
                                action.type(),
                                seconds
                        )
                )
        );
    }

    private void createPreviousButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout
    ) {

        createButton(
                menu,
                state,
                layout,
                0,
                "▲",
                button -> {

                    DAI_ActionQueue.previous();

                    DAI_Core.LOGGER.debug(
                            "<DAI>: Selected previous queued action."
                    );
                }
        );
    }

    private void createSelectedButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout
    ) {

        createButton(
                menu,
                state,
                layout,
                1,
                "",
                button -> {

                    int selectedIndex =
                            DAI_ActionQueue.selectedIndex();

                    DAI_ActionDefinition selectedAction =
                            DAI_ActionQueue.selected();

                    if (selectedAction == null) {

                        DAI_Core.LOGGER.debug(
                                "<DAI>: Queue removal ignored because the queue is empty."
                        );

                        return;
                    }

                    DAI_Core.LOGGER.debug(
                            "<DAI>: Removing queued action at index {} with type '{}'.",
                            selectedIndex,
                            selectedAction.type()
                    );

                    DAI_ActionQueue.remove(
                            selectedIndex
                    );
                }
        );
    }

    private void createNextButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout
    ) {

        createButton(
                menu,
                state,
                layout,
                2,
                "▼",
                button -> {

                    DAI_ActionQueue.next();

                    DAI_Core.LOGGER.debug(
                            "<DAI>: Selected next queued action."
                    );
                }
        );
    }

    private void createButton(
            DAI_MenuCore menu,
            DAI_MenuState state,
            DAI_MenuLayout layout,
            int slot,
            String text,
            Button.OnPress action
    ) {

        Button existingButton =
                state.subButton(
                        DAI_MenuCategory.SYSTEM,
                        slot
                );

        if (existingButton != null) {
            menu.removeMenuWidget(existingButton);
        }

        DAI_Layout.Layout buttonLayout =
                layout.sub(
                        DAI_MenuCategory.SYSTEM,
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
                DAI_MenuCategory.SYSTEM,
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
}