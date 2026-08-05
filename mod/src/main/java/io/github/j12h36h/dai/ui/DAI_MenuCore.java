package io.github.j12h36h.dai.ui;

import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.action.DAI_ActionExecutor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
public class DAI_MenuCore extends Screen {

    private final DAI_MenuState state =
            new DAI_MenuState();

    private final DAI_MenuLayout layout =
            new DAI_MenuLayout();

    private final DAI_MenuDatapack datapackMenu =
            new DAI_MenuDatapack();

    private final DAI_MenuQueue queueMenu =
            new DAI_MenuQueue();

    private final DAI_MenuHotbar hotbarMenu =
            new DAI_MenuHotbar();

    private final DAI_MenuAutomation automationMenu =
            new DAI_MenuAutomation();

    private long lastQueueRevision = -1L;

    public DAI_MenuCore() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();

        state.reset();

        layout.initialize(
                width,
                height
        );

        createRootButton(
                DAI_MenuCategory.SYSTEM,
                "System"
        );

        createRootButton(
                DAI_MenuCategory.ACTION,
                "Actions"
        );

        updateSystemRootButton();
        updateActionRootButton("default");

        DAI_Core.LOGGER.debug(
                "<DAI>: Menu core initialized."
        );
    }

    private void createRootButton(
            DAI_MenuCategory category,
            String text
    ) {

        DAI_Layout.Layout buttonLayout =
                layout.root(category);

        Button button =
                Button.builder(
                                Component.literal(text),
                                pressedButton ->
                                        toggleMenu(category)
                        )
                        .bounds(
                                buttonLayout.x(),
                                buttonLayout.y(),
                                buttonLayout.width(),
                                buttonLayout.height()
                        )
                        .build();

        state.setRootButton(
                category,
                button
        );

        addMenuWidget(button);
    }

    private void toggleMenu(
            DAI_MenuCategory category
    ) {

        if (state.isOpen(category)) {

            closeMenu(category);

            return;
        }

        updateMenu(
                category.name(),
                "default"
        );
    }

    public void updateMenu(
            String menu,
            String open
    ) {

        if (menu == null || menu.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot update an empty menu."
            );

            return;
        }

        if (open == null || open.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open an empty menu definition."
            );

            return;
        }

        DAI_MenuCategory category;

        try {

            category = DAI_MenuCategory.valueOf(
                    menu.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unknown menu '{}'.",
                    menu
            );

            return;
        }

        String normalizedOpen =
                open.trim().toLowerCase(Locale.ROOT);

        DAI_Core.LOGGER.debug(
                "<DAI>: Updating {} menu with definition '{}'.",
                category,
                normalizedOpen
        );

        if (
                category == DAI_MenuCategory.SYSTEM
                        && normalizedOpen.equals("queue")
        ) {

            queueMenu.open(
                    this,
                    state,
                    layout
            );

            updateSystemRootButton();

            return;
        }

        if (
                category == DAI_MenuCategory.SYSTEM
                        && normalizedOpen.equals("hotbar")
        ) {

            hotbarMenu.open(
                    this,
                    state,
                    layout
            );

            updateSystemRootButton();

            return;
        }

        if (
                category == DAI_MenuCategory.ACTION
                        && normalizedOpen.equals("automation")
        ) {

            automationMenu.open(
                    this,
                    state,
                    layout
            );

            updateActionRootButton(
                    normalizedOpen
            );

            return;
        }

        if (category == DAI_MenuCategory.SYSTEM) {

            state.setSystemMode(
                    DAI_MenuState.SystemMode.DATAPACK
            );

            updateSystemRootButton();
        }

        if (category == DAI_MenuCategory.ACTION) {

            state.setActionMode(
                    DAI_MenuState.ActionMode.DATAPACK
            );
        }

        datapackMenu.open(
                this,
                state,
                layout,
                category,
                normalizedOpen
        );

        if (category == DAI_MenuCategory.ACTION) {

            updateActionRootButton(
                    normalizedOpen
            );
        }
    }

    void addMenuWidget(
            Button button
    ) {

        if (button == null) {
            return;
        }

        addRenderableWidget(button);
    }

    void removeMenuWidget(
            Button button
    ) {

        if (button == null) {
            return;
        }

        removeWidget(button);
    }

    void runAction(
            String action
    ) {

        if (action == null || action.isBlank()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot run an empty menu action."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Running menu action '{}'.",
                action
        );

        DAI_ActionExecutor.execute(
                action.trim()
        );
    }

    void closeMenu(
            DAI_MenuCategory category
    ) {

        state.setOpen(
                category,
                false
        );

        for (
                int slot = 0;
                slot < DAI_MenuState.SUBMENU_SLOT_COUNT;
                slot++
        ) {

            Button button =
                    state.removeSubButton(
                            category,
                            slot
                    );

            if (button != null) {
                removeMenuWidget(button);
            }
        }

        if (category == DAI_MenuCategory.SYSTEM) {

            state.setSystemMode(
                    DAI_MenuState.SystemMode.DATAPACK
            );

            updateSystemRootButton();
        }

        if (category == DAI_MenuCategory.ACTION) {

            state.setActionMode(
                    DAI_MenuState.ActionMode.DATAPACK
            );

            updateActionRootButton(
                    "default"
            );
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (
                state.systemMode() == DAI_MenuState.SystemMode.QUEUE
                        && lastQueueRevision != DAI_ActionQueue.revision()
        ) {

            lastQueueRevision =
                    DAI_ActionQueue.revision();

            queueMenu.refresh(state);
        }

        if (
                state.systemMode() == DAI_MenuState.SystemMode.HOTBAR
        ) {

            hotbarMenu.refresh(state);
        }
    }

    private void updateSystemRootButton() {

        Button button =
                state.rootButton(
                        DAI_MenuCategory.SYSTEM
                );

        if (button == null) {
            return;
        }

        Component text =
                switch (state.systemMode()) {

                    case QUEUE ->
                            Component.literal("Queue");

                    case HOTBAR ->
                            Component.literal("Hotbar");

                    default ->
                            Component.literal("System");
                };

        button.setMessage(text);
    }

    private void updateActionRootButton(
            String menuName
    ) {

        Button button =
                state.rootButton(
                        DAI_MenuCategory.ACTION
                );

        if (button == null) {
            return;
        }

        if (
                menuName == null
                        || menuName.isBlank()
                        || menuName.equalsIgnoreCase("default")
        ) {

            button.setMessage(
                    Component.literal("Actions")
            );

            return;
        }

        String formattedName =
                Arrays.stream(
                                menuName
                                        .trim()
                                        .toLowerCase(Locale.ROOT)
                                        .split("_")
                        )
                        .filter(part -> !part.isBlank())
                        .map(part ->
                                Character.toUpperCase(part.charAt(0))
                                        + part.substring(1)
                        )
                        .collect(
                                Collectors.joining(" ")
                        );

        button.setMessage(
                Component.literal(formattedName)
        );
    }

    @Override
    public void extractRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {

        super.extractRenderState(
                graphics,
                mouseX,
                mouseY,
                partialTick
        );
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}