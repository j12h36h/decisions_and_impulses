package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_MoveController;
import io.github.j12h36h.dai.input.DAI_InputController;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import io.github.j12h36h.dai.input.DAI_TargetController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;

public final class DAI_ActionLogic {

    private DAI_ActionLogic() {
        // Utility class.
    }

    public static void execute(DAI_ActionCore action) {

        if (action == null) {

            DAI_Core.LOGGER.error(
                    "<DAI>: Cannot execute a null action."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Executing action type='{}', sequence={}.",
                action.type(),
                action.sequence().size()
        );

        DAI_ActionRegistry.execute(action);
    }

    public static void attack(
            DAI_ActionCore action
    ) {

        if (DAI_TargetController.selected() == null) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: No selected attack target."
            );
            return;
        }

        DAI_ActionController.requestAttack();
    }

    public static void requestUpdateMenu(
            DAI_ActionCore action
    ) {

        updateMenu(
                action.menu(),
                action.open()
        );
    }

    public static void requestOpenPause(
            DAI_ActionCore action
    ) {

        openPauseMenu();
    }

    public static void requestOpenInventory(
            DAI_ActionCore action
    ) {

        openInventory();
    }

    public static void requestLook(
            DAI_ActionCore action
    ) {

        look(
                action.yaw(),
                action.pitch()
        );
    }

    public static void requestSequence(
            DAI_ActionCore action
    ) {
        DAI_Core.LOGGER.warn(
                "<DAI>: Sequence reached execution without prior resolution."
        );
    }

    public static void move(
            DAI_ActionCore action
    ) {

        startDirectionalMovement(
                action.direction(),
                action.ticks()
        );
    }

    public static void delay(
            DAI_ActionCore action
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Delaying action queue for {} tick(s).",
                action.ticks()
        );

        DAI_ActionQueue.delay(
                action.ticks()
        );
    }

    public static void requestJump(
            DAI_ActionCore action
    ) {

        String direction =
                normalize(action.direction());

        if (!direction.isEmpty()) {

            startDirectionalMovement(
                    direction,
                    1
            );
        }

        jump();
    }

    public static void requestCrouchToggle(
            DAI_ActionCore action
    ) {

        crouchToggle();
    }

    public static void requestSprintToggle(
            DAI_ActionCore action
    ) {

        sprintToggle();
    }

    private static void openInventory() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open inventory because the player is null."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening inventory screen."
        );

        DAI_ScreenManager.openTemporary(
                minecraft.gui.screen(),
                new InventoryScreen(minecraft.player)
        );
    }

    private static void openPauseMenu() {

        Minecraft minecraft =
                Minecraft.getInstance();

        DAI_Core.LOGGER.debug(
                "<DAI>: Opening pause screen."
        );

        DAI_ScreenManager.openTemporary(
                minecraft.gui.screen(),
                new PauseScreen(true)
        );
    }

    private static void updateMenu(
            String menu,
            String open
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        DAI_MenuCore daiMenu = null;

        if (
                minecraft.gui.screen()
                        instanceof DAI_MenuCore currentMenu
        ) {

            daiMenu = currentMenu;

        } else if (
                DAI_ScreenManager.peek()
                        instanceof DAI_MenuCore stackedMenu
        ) {

            daiMenu = stackedMenu;
        }

        if (daiMenu == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: No active DAI menu was found to update."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Updating menu='{}', open='{}'.",
                menu,
                open
        );

        daiMenu.updateMenu(
                menu,
                open
        );
    }

    private static void look(
            float yaw,
            float pitch
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Setting look rotation to yaw={}, pitch={}.",
                yaw,
                pitch
        );

        DAI_InputController
                .look()
                .setRotation(
                        yaw,
                        pitch
                );
    }

    private static void startDirectionalMovement(
            String direction,
            int ticks
    ) {

        String normalizedDirection =
                normalize(direction);

        if (ticks <= 0) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot move '{}' for {} tick(s).",
                    normalizedDirection,
                    ticks
            );

            return;
        }

        switch (normalizedDirection) {

            case "forward" ->
                    DAI_MoveController.start(
                            1.0F,
                            0.0F,
                            ticks
                    );

            case "backward" ->
                    DAI_MoveController.start(
                            -1.0F,
                            0.0F,
                            ticks
                    );

            case "left" ->
                    DAI_MoveController.start(
                            0.0F,
                            1.0F,
                            ticks
                    );

            case "right" ->
                    DAI_MoveController.start(
                            0.0F,
                            -1.0F,
                            ticks
                    );

            default ->
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Unknown movement direction '{}'.",
                            direction
                    );
        }
    }

    private static void jump() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot jump because the player is null."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Requesting player jump."
        );

        minecraft.player.jumpFromGround();
    }

    private static void crouchToggle() {

        DAI_Core.LOGGER.debug(
                "<DAI>: Toggling crouch input."
        );

        DAI_InputController.movement().setSneak(
                !DAI_InputController.movement().sneak()
        );
    }

    private static void sprintToggle() {

        DAI_Core.LOGGER.debug(
                "<DAI>: Toggling sprint input."
        );

        DAI_InputController.movement().setSprint(
                !DAI_InputController.movement().sprint()
        );
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}