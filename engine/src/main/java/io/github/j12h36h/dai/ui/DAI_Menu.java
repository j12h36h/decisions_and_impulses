package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.core.Config;
import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.awt.*;
import java.util.List;
import java.util.EnumMap;

public class DAI_Menu extends Screen {

    private enum MenuType {
        SYSTEM,
        IMPULSE,
        DECISION
    }

    private final EnumMap<MenuType, Button> rootButtons = new EnumMap<>(MenuType.class);
    private final EnumMap<MenuType, Button[]> subButtons = new EnumMap<>(MenuType.class);
    private final EnumMap<MenuType, Boolean> menuOpen = new EnumMap<>(MenuType.class);
    private final EnumMap<MenuType, DAI_Layout.Layout> layouts = new EnumMap<>(MenuType.class);

    public DAI_Menu() {
        super(Component.empty());

        for (MenuType type : MenuType.values()) {
            subButtons.put(type, new Button[3]);
            menuOpen.put(type, false);
        }
    }

    @Override
    protected void init() {
        super.init();

        layouts.put(
                MenuType.SYSTEM,
                DAI_Layout.getLayout(
                        DAI_Position.valueOf(Config.MENU_POSITION.get()),
                        width, height,
                        150, 20,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN
                )
        );

        layouts.put(
                MenuType.IMPULSE,
                DAI_Layout.getLayout(
                        DAI_Position.valueOf(Config.IMPULSE_POSITION.get()),
                        width, height,
                        150, 20,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN
                )
        );

        layouts.put(
                MenuType.DECISION,
                DAI_Layout.getLayout(
                        DAI_Position.valueOf(Config.DECISION_POSITION.get()),
                        width, height,
                        150, 20,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN,
                        DAI_Layout.DEFAULT_MARGIN
                )
        );

        createRootButton(MenuType.SYSTEM, "System", b -> toggleMenu(MenuType.SYSTEM));
        createRootButton(MenuType.IMPULSE, "Impulses", b -> toggleMenu(MenuType.IMPULSE));
        createRootButton(MenuType.DECISION, "Decisions", b -> toggleMenu(MenuType.DECISION));
    }

    private void createRootButton(MenuType type, String title, Button.OnPress action) {

        DAI_Layout.Layout layout = layouts.get(type);

        Button button = Button.builder(Component.literal(title), action)
                .bounds(
                        layout.x(),
                        layout.y(),
                        layout.width(),
                        layout.height()
                )
                .build();

        rootButtons.put(type, button);
        addRenderableWidget(button);
    }

    private void toggleMenu(MenuType type) {

        if (Boolean.TRUE.equals(menuOpen.get(type))) {
            closeMenu(type);
            return;
        }

        switch (type) {

            case SYSTEM ->
                    openDatapackMenu(type, DAI_MenuCategory.SYSTEM);

            case IMPULSE ->
                    openDatapackMenu(type, DAI_MenuCategory.IMPULSE);

            case DECISION ->
                    openDatapackMenu(type, DAI_MenuCategory.DECISION);
        }
    }

    private void openDatapackMenu(MenuType type, DAI_MenuCategory category) {

        List<DAI_SystemDefinition> systems =
                DAI_SystemManager.get(category);

        if (systems.isEmpty()) {
            DAI.LOGGER.warn("<DAI>: No definitions loaded for {}", category);
            return;
        }

        menuOpen.put(type, true);

        Button[] buttons = subButtons.get(type);
        DAI_Layout.Layout layout = layouts.get(type);

        for (DAI_SystemDefinition system : systems) {

            for (DAI_SystemButton definition : system.buttons()) {

                int slot = definition.slot();

                if (slot < 0 || slot >= buttons.length) {
                    DAI.LOGGER.warn(
                            "<DAI>: Invalid slot {} in {}",
                            slot,
                            category
                    );
                    continue;
                }

                DAI_Layout.Layout subLayout =
                        DAI_Layout.getSubLayout(layout, slot);

                buttons[slot] = Button.builder(
                                Component.literal(definition.text()),
                                b -> runAction(definition.action())
                        )
                        .bounds(
                                subLayout.x(),
                                subLayout.y(),
                                subLayout.width(),
                                subLayout.height()
                        )
                        .build();

                addRenderableWidget(buttons[slot]);
            }
        }
    }

    private void closeMenu(MenuType type) {

        menuOpen.put(type, false);

        Button[] buttons = subButtons.get(type);

        for (int i = 0; i < buttons.length; i++) {

            if (buttons[i] != null) {
                removeWidget(buttons[i]);
                buttons[i] = null;
            }
        }
    }

    private void runAction(String action) {

        DAI_ScreenManager.push(this);

        DAI_ActionExecutor.execute(action);
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractBackground(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Intentionally empty.
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}