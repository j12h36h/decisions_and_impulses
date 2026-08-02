package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.action.DAI_Action;
import io.github.j12h36h.dai.action.DAI_ActionExecutor;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.core.Config;
import io.github.j12h36h.dai.core.DAI;
import io.github.j12h36h.dai.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.EnumMap;

public class DAI_Menu extends Screen {

    private enum MenuType {
        SYSTEM,
        IMPULSE,
        DECISION
    }

    private enum SystemMode {
        DATAPACK,
        QUEUE
    }

    private SystemMode systemMode = SystemMode.DATAPACK;
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

        updateMenu(type.name(), "default");
    }

    private void openDatapackMenu(
            MenuType type,
            DAI_MenuCategory category,
            String id
    ) {

        closeMenu(type);

        DAI_SystemDefinition system =
                DAI_SystemManager.get(category, id);

        if (system == null) {

            DAI.LOGGER.warn(
                    "<DAI>: Unknown {} menu '{}'",
                    category,
                    id
            );

            return;
        }

        menuOpen.put(type, true);

        Button[] buttons = subButtons.get(type);
        DAI_Layout.Layout layout = layouts.get(type);

        for (DAI_SystemButton definition : system.buttons()) {

            int slot = definition.slot();

            if (slot < 0 || slot >= buttons.length) {

                DAI.LOGGER.warn(
                        "<DAI>: Invalid slot {} in {}:{}",
                        slot,
                        category,
                        id
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

    public void updateMenu(String menu, String open) {

        MenuType type;

        try {
            type = MenuType.valueOf(menu.toUpperCase());
        } catch (IllegalArgumentException exception) {
            DAI.LOGGER.warn("<DAI>: Unknown menu '{}'", menu);
            return;
        }

        if (type == MenuType.SYSTEM && open.equals("queue")) {
            systemMode = SystemMode.QUEUE;
            openQueue();
            return;
        }

        if (type == MenuType.SYSTEM) {
            systemMode = SystemMode.DATAPACK;
        }

        DAI_MenuCategory category = switch (type) {
            case SYSTEM -> DAI_MenuCategory.SYSTEM;
            case IMPULSE -> DAI_MenuCategory.IMPULSE;
            case DECISION -> DAI_MenuCategory.DECISION;
        };

        openDatapackMenu(type, category, open);
    }

    private void openQueue() {

        closeMenu(MenuType.SYSTEM);

        menuOpen.put(MenuType.SYSTEM, true);

        Button[] buttons = subButtons.get(MenuType.SYSTEM);
        DAI_Layout.Layout layout = layouts.get(MenuType.SYSTEM);

        // ▲ Previous
        DAI_Layout.Layout top = DAI_Layout.getSubLayout(layout, 0);

        buttons[0] = Button.builder(
                Component.literal("▲"),
                b -> {
                    DAI_ActionQueue.previous();
                    refreshQueue();
                }
        ).bounds(
                top.x(),
                top.y(),
                top.width(),
                top.height()
        ).build();

        addRenderableWidget(buttons[0]);

        // Selected Action
        DAI_Layout.Layout middle = DAI_Layout.getSubLayout(layout, 1);

        buttons[1] = Button.builder(
                Component.literal(""),
                b -> {
                    DAI_ActionQueue.remove(
                            DAI_ActionQueue.selectedIndex()
                    );
                    refreshQueue();
                }
        ).bounds(
                middle.x(),
                middle.y(),
                middle.width(),
                middle.height()
        ).build();

        addRenderableWidget(buttons[1]);

        // ▼ Next
        DAI_Layout.Layout bottom = DAI_Layout.getSubLayout(layout, 2);

        buttons[2] = Button.builder(
                Component.literal("▼"),
                b -> {
                    DAI_ActionQueue.next();
                    refreshQueue();
                }
        ).bounds(
                bottom.x(),
                bottom.y(),
                bottom.width(),
                bottom.height()
        ).build();

        addRenderableWidget(buttons[2]);

        refreshQueue();
    }

    private void refreshQueue() {

        Button[] buttons = subButtons.get(MenuType.SYSTEM);

        if (buttons == null || buttons[1] == null) {
            return;
        }

        DAI_Action action = DAI_ActionQueue.selected();

        if (action == null) {
            buttons[1].setMessage(Component.literal("[ Empty ]"));
            return;
        }

        double seconds = DAI_ActionQueue.delayTicks() / 20.0;

        buttons[1].setMessage(
                Component.literal(
                        "{"
                                + (DAI_ActionQueue.selectedIndex() + 1)
                                + "} "
                                + action.type()
                                + " ("
                                + String.format("%.1fs", seconds)
                                + ")"
                )
        );
    }

    @Override
    public void tick() {
        super.tick();

        if (systemMode == SystemMode.QUEUE) {
            refreshQueue();
        }
    }
}