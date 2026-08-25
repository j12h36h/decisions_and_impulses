package io.github.j12h36h.dai.client.menus;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.j12h36h.dai.client.logics.DAI_AutomationLogic;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.logics.DAI_ActionLogic;
import io.github.j12h36h.dai.client.menus.system.DAI_ClientRuntime;
import io.github.j12h36h.dai.client.menus.system.DAI_ButtonStyle;
import io.github.j12h36h.dai.client.menus.system.DAI_SystemManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class DAI_MenuCore extends Screen {

    private static final DAI_ButtonStyle ROOT_SYSTEM_STYLE = new DAI_ButtonStyle(
            "#B0140B09", "#DE35170A", "#E95A2710", "#FFF4EA", "#FF8A2A");
    private static final DAI_ButtonStyle ROOT_ACTION_STYLE = new DAI_ButtonStyle(
            "#B0100918", "#DE27123B", "#E6421C60", "#FAF0FF", "#A855F7");

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

    private final DAI_MenuAvailable availableMenu =
            new DAI_MenuAvailable();

    private long lastQueueRevision = -1L;
    private boolean focusedMode;

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
                "CREATE"
        );

        createRootButton(
                DAI_MenuCategory.ACTION,
                "AUTOMATE"
        );

        updateSystemRootButton();
        updateActionRootButton("default");

        DAI_Core.debug(
                "<DAI>: Menu core initialized."
        );
    }

    private void createRootButton(
            DAI_MenuCategory category,
            String text
    ) {

        DAI_Layout.Layout buttonLayout =
                layout.root(category);

        Button button = new DAI_StyledButton(
                buttonLayout.x(),
                buttonLayout.y(),
                buttonLayout.width(),
                buttonLayout.height(),
                Component.literal(text),
                pressedButton -> toggleMenu(category),
                category == DAI_MenuCategory.SYSTEM ? ROOT_SYSTEM_STYLE : ROOT_ACTION_STYLE
        );

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

    /**
     * Focused presentation mode is intended for scripted one-choice prompts.
     * It hides the normal DAI root controls and positions datapack buttons in
     * a centered dialogue lane without changing the underlying menu system.
     */
    public void setFocusedMode(boolean focused) {
        this.focusedMode = focused;
        if (!focused) return;
        for (DAI_MenuCategory category : DAI_MenuCategory.values()) {
            Button rootButton = state.rootButton(category);
            if (rootButton != null) removeMenuWidget(rootButton);
        }
    }

    public boolean focusedMode() { return focusedMode; }

    public DAI_Layout.Layout focusedSub(int slot) {
        int buttonWidth = Math.max(120, Math.min(320, width - 40));
        int buttonHeight = 20;
        int x = (width - buttonWidth) / 2;
        int y = (height / 2) + 10 + (slot * (buttonHeight + 4));
        return new DAI_Layout.Layout(io.github.j12h36h.dai.logics.core.DAI_Position.MID_LEFT, x, y, buttonWidth, buttonHeight);
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

        DAI_Core.debug(
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

        if (
                category == DAI_MenuCategory.ACTION
                        && DAI_SystemManager.isAvailableActionMenu(
                        normalizedOpen
                )
        ) {

            availableMenu.open(
                    this,
                    state,
                    layout,
                    normalizedOpen
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

        DAI_Core.debug(
                "<DAI>: Running menu action '{}'.",
                action
        );

        var resolved =
                DAI_ActionResolver.resolve(
                        action.trim()
                );

        if (resolved.isEmpty()) {
            return;
        }

        /*
         * Menu input is the user control plane. It always outranks queued
         * autonomous work. Cancel current controller ownership first, then
         * synchronously dispatch the selected menu action. This makes Stop
         * effective immediately instead of waiting behind a barrier/timeout.
         */
        DAI_AutomationLogic.interruptWorkForMenuAction();
        DAI_ActionQueue.interruptAndDispatch(resolved);
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

        if (
                state.actionMode() == DAI_MenuState.ActionMode.AVAILABLE
        ) {

            availableMenu.refresh(state);
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
                            Component.literal("Create");
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
                    Component.literal("Automate")
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
        if (focusedMode) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        // Website/index-universe inspired control-plane framing. The live
        // world remains visible; only sparse orange/purple wire geometry is
        // added behind the compact menu controls.
        graphics.fillGradient(0, 0, width, 34, 0xB0090610, 0x7012091B);
        graphics.text(font, Component.literal("D.A.I. // UNIVERSAL CONTROL"), 10, 8, 0xFFFF9B45);
        graphics.text(font, Component.literal("CREATOR / AUTOMATION / RUNTIME"), 10, 19, 0xFFC58AEF);
        int cx = width / 2;
        int cy = height / 2;
        graphics.outline(cx - 34, cy - 18, 68, 36, 0x2FA855F7);
        graphics.outline(cx - 52, cy - 28, 104, 56, 0x22FF8428);
        graphics.fill(cx - 1, cy - 46, cx + 1, cy - 22, 0x33A855F7);
        graphics.fill(cx - 68, cy, cx - 42, cy + 1, 0x33FF8428);
        graphics.fill(cx + 42, cy, cx + 68, cy + 1, 0x33A855F7);

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

    @Override
    public boolean keyPressed(
            KeyEvent event
    ) {

        if (
                InputConstants.getKey(event)
                        .getValue()
                        == GLFW.GLFW_KEY_GRAVE_ACCENT
        ) {

            DAI_ScreenManager.clear();

            minecraft.gui.setScreen(
                    null
            );

            DAI_InputState.setCursorReleased(
                    false
            );

            DAI_ClientRuntime.updateMouseCapture();

            DAI_Core.debug(
                    "<DAI>: DAI menu closed."
            );

            return true;
        }

        return super.keyPressed(
                event
        );
    }

}