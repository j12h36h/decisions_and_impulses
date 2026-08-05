package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class DAI_MenuHotbar {

    public DAI_MenuHotbar() {
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

        DAI_HotbarController.open();

        DAI_MenuCategory category =
                DAI_MenuCategory.SYSTEM;

        menu.closeMenu(category);

        state.setSystemMode(
                DAI_MenuState.SystemMode.HOTBAR
        );

        state.setOpen(
                category,
                true
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening hotbar menu."
        );

        DAI_MenuSelector.create(
                menu,
                state,
                layout,
                category,
                button -> {

                    DAI_HotbarController.previous();

                    refresh(state);
                },
                button -> {

                    DAI_HotbarController.select();

                    refresh(state);
                },
                button -> {

                    DAI_HotbarController.next();

                    refresh(state);
                }
        );

        refresh(state);

        DAI_Core.LOGGER.debug(
                "<DAI>: Hotbar menu opened."
        );
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

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            selectedButton.setMessage(
                    Component.literal("[ No Player ]")
            );

            return;
        }

        int slot =
                DAI_HotbarController.selectedSlot();

        int activeSlot =
                minecraft.player
                        .getInventory()
                        .getSelectedSlot();

        boolean isSelected =
                slot == activeSlot;

        String prefix =
                isSelected
                        ? "[Selected]"
                        : "[Preview]";

        ItemStack stack =
                minecraft.player
                        .getInventory()
                        .getItem(slot);

        if (stack.isEmpty()) {

            selectedButton.setMessage(
                    Component.literal(
                            String.format(
                                    Locale.ROOT,
                                    "%s {%d} [ Empty ]",
                                    prefix,
                                    slot + 1
                            )
                    )
            );

            return;
        }

        selectedButton.setMessage(
                Component.literal(
                        String.format(
                                Locale.ROOT,
                                "%s {%d} %s x%d",
                                prefix,
                                slot + 1,
                                stack.getHoverName().getString(),
                                stack.getCount()
                        )
                )
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
}