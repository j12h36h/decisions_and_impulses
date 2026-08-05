package io.github.j12h36h.dai.action;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.*;
import io.github.j12h36h.dai.recognition.DAI_RecogController;
import io.github.j12h36h.dai.ui.DAI_HotbarController;
import io.github.j12h36h.dai.ui.DAI_MenuCore;
import io.github.j12h36h.dai.ui.DAI_ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class DAI_ActionLogic {

    private static final int DIRECTIONAL_JUMP_COOLDOWN_TICKS = 10;

    private static long nextDirectionalJumpTick;

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

    public static void requestTargetAttack(
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

    public static void requestMineNearestBlock(
            DAI_ActionCore action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: mine_nearest_block requires a block id or block tag in 'action'."
            );

            return;
        }

        double searchRadius =
                action.value() > 0.0D
                        ? action.value()
                        : 16.0D;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : 200;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "recognize_block",
                                action.action(),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                searchRadius
                        ),
                        new DAI_ActionCore(
                                "approach_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                timeoutTicks,
                                0,
                                false,
                                3.25D
                        ),
                        new DAI_ActionCore(
                                "wait_for_approach",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                timeoutTicks,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                20,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "mine_targeted_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                20,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued nearest-block mining for '{}' within radius {}.",
                action.action(),
                searchRadius
        );
    }
    public static void requestPlaceNearestBlock(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot place a nearby block without an active player and level."
            );

            return;
        }

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: place_nearest_block requires a block item id in 'action'."
            );

            return;
        }

        Identifier blockItemId =
                parseItemId(
                        action.action()
                );

        if (blockItemId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid block item id '{}'.",
                    action.action()
            );

            return;
        }

        Item item =
                BuiltInRegistries.ITEM.getValue(
                        blockItemId
                );

        if (
                item == null
                        || item == Items.AIR
                        || !(item instanceof BlockItem)
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Item '{}' is not a placeable block item.",
                    blockItemId
            );

            return;
        }

        int searchRadius =
                action.value() > 0.0D
                        ? Mth.clamp(
                        (int) Math.round(
                                action.value()
                        ),
                        1,
                        32
                )
                        : 8;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : 200;

        BlockPos support =
                findNearestPlacementSupport(
                        minecraft,
                        searchRadius
                );

        if (support == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: No valid placement support was found within {} block(s).",
                    searchRadius
            );

            return;
        }

        DAI_TargetController.selectBlock(
                support
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "approach_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                timeoutTicks,
                                0,
                                false,
                                3.25D
                        ),
                        new DAI_ActionCore(
                                "wait_for_approach",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                timeoutTicks,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                20,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "place_targeted_block",
                                blockItemId.toString(),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued placement of '{}' using support block {} within radius {}.",
                blockItemId,
                support,
                searchRadius
        );
    }

    private static BlockPos findNearestPlacementSupport(
            Minecraft minecraft,
            int radius
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return null;
        }

        BlockPos playerPos =
                minecraft.player.blockPosition();

        BlockPos nearest =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        BlockPos minimum =
                playerPos.offset(
                        -radius,
                        -radius,
                        -radius
                );

        BlockPos maximum =
                playerPos.offset(
                        radius,
                        radius,
                        radius
                );

        for (
                BlockPos candidate
                : BlockPos.betweenClosed(
                minimum,
                maximum
        )
        ) {

            BlockState state =
                    minecraft.level.getBlockState(
                            candidate
                    );

            if (state.isAir()) {
                continue;
            }

            boolean hasPlaceableAdjacentSpace =
                    false;

            for (Direction direction : Direction.values()) {

                BlockPos adjacent =
                        candidate.relative(
                                direction
                        );

                if (
                        minecraft.level
                                .getBlockState(
                                        adjacent
                                )
                                .canBeReplaced()
                ) {

                    hasPlaceableAdjacentSpace =
                            true;

                    break;
                }
            }

            if (!hasPlaceableAdjacentSpace) {
                continue;
            }

            double distanceSquared =
                    candidate.distSqr(
                            playerPos
                    );

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearest =
                    candidate.immutable();
        }

        return nearest;
    }

    public static void requestEatBestFood(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot eat without an active player and game mode."
            );

            return;
        }

        if (
                minecraft.player.getFoodData()
                        .getFoodLevel() >= 20
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Player is not hungry; eat_best_food skipped."
            );

            return;
        }

        Identifier[] candidates =
                identifiers(
                        "minecraft:enchanted_golden_apple",
                        "minecraft:golden_apple",
                        "minecraft:golden_carrot",
                        "minecraft:cooked_beef",
                        "minecraft:cooked_porkchop",
                        "minecraft:cooked_mutton",
                        "minecraft:cooked_chicken",
                        "minecraft:cooked_rabbit",
                        "minecraft:cooked_cod",
                        "minecraft:cooked_salmon",
                        "minecraft:rabbit_stew",
                        "minecraft:mushroom_stew",
                        "minecraft:pumpkin_pie",
                        "minecraft:bread",
                        "minecraft:baked_potato",
                        "minecraft:apple",
                        "minecraft:carrot",
                        "minecraft:melon_slice",
                        "minecraft:sweet_berries",
                        "minecraft:glow_berries",
                        "minecraft:dried_kelp"
                );

        Identifier selectedFood =
                null;

        for (Identifier candidate : candidates) {

            if (
                    DAI_HotbarController.selectItem(
                            candidate
                    )
            ) {

                selectedFood =
                        candidate;

                break;
            }
        }

        if (selectedFood == null) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: No supported food was found in the player inventory."
            );

            return;
        }

        int useTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : 40;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                2,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "use_start",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                useTicks,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "use_stop",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued eating '{}' for {} tick(s).",
                selectedFood,
                useTicks
        );
    }
    public static void requestAttackTarget(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot attack a target without an active player and level."
            );

            return;
        }

        Entity target =
                DAI_TargetController.selected();

        if (target == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: attack_target requires a selected entity target."
            );

            return;
        }

        if (
                !target.isAlive()
                        || target.isRemoved()
        ) {

            DAI_TargetController.clearEntity();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected attack target is no longer valid."
            );

            return;
        }

        requestEquipBestWeapon(
                action
        );

        int attackTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : 100;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                2,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "attack_start",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                attackTicks,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "attack_stop",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued attack against target '{}' for {} tick(s).",
                target.getName().getString(),
                attackTicks
        );
    }

    public static void requestRecognizeTarget(
            DAI_ActionCore action
    ) {

        DAI_RecogController.recognizeTarget();
    }

    public static void openChat(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        Screen currentScreen =
                minecraft.gui.screen();

        if (currentScreen instanceof DAI_MenuCore) {

            DAI_ScreenManager.push(
                    currentScreen
            );
        }

        DAI_ScreenManager.open(
                new ChatScreen(
                        "",
                        false,
                        true
                )
        );
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

    public static void requestSetLook(
            DAI_ActionCore action
    ) {

        setLook(
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

    public static void requestItemUse(
            DAI_ActionCore action
    ) {

        DAI_ActionController.requestUse();
    }

    public static void requestItemDrop(
            DAI_ActionCore action
    ) {

        DAI_ActionController.requestDrop();
    }

    public static void requestHandSwap(
            DAI_ActionCore action
    ) {

        DAI_ActionController.requestSwap();
    }

    public static void requestBasicAttack(
            DAI_ActionCore action
    ) {
        DAI_ActionController.requestAttack();
    }

    public static void requestJump(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot jump because the player or level is unavailable."
            );

            return;
        }

        String direction =
                normalize(
                        action.direction()
                );

        boolean directionalJump =
                !direction.isEmpty();

        long currentTick =
                minecraft.level.getGameTime();

        /*
         * Directional jumps act as dash-jumps. A cooldown prevents
         * multiple activations from applying repeated upward impulses
         * while the client's grounded state is still catching up.
         */
        if (
                directionalJump
                        && currentTick < nextDirectionalJumpTick
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Directional jump blocked by cooldown; remaining={} tick(s).",
                    nextDirectionalJumpTick - currentTick
            );

            return;
        }

        boolean inFluid =
                minecraft.player.isInWater()
                        || minecraft.player.isInLava();

        boolean airborne =
                !minecraft.player.onGround()
                        && !inFluid;

        /*
         * The velocity check closes the brief client-side window where
         * onGround may still be true immediately after jumping.
         */
        boolean alreadyRising =
                minecraft.player
                        .getDeltaMovement()
                        .y > 0.01D;

        if (
                airborne
                        || (
                        directionalJump
                                && alreadyRising
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Jump blocked because the player is airborne or already rising."
            );

            return;
        }

        if (directionalJump) {

            int movementTicks =
                    action.ticks() > 0
                            ? action.ticks()
                            : 5;

            startDirectionalMovement(
                    direction,
                    movementTicks
            );

            nextDirectionalJumpTick =
                    currentTick
                            + DIRECTIONAL_JUMP_COOLDOWN_TICKS;

            DAI_Core.LOGGER.debug(
                    "<DAI>: Directional jump requested direction='{}', ticks={}, cooldown={}.",
                    direction,
                    movementTicks,
                    DIRECTIONAL_JUMP_COOLDOWN_TICKS
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

    public static void requestCrouchSet(
            DAI_ActionCore action
    ) {

        DAI_InputController.movement()
                .setSneak(action.state());

        DAI_Core.LOGGER.debug(
                "<DAI>: Crouch input {}.",
                action.state() ? "enabled" : "disabled"
        );
    }

    public static void requestSprintSet(
            DAI_ActionCore action
    ) {

        DAI_InputController.movement()
                .setSprint(action.state());

        DAI_Core.LOGGER.debug(
                "<DAI>: Sprint input {}.",
                action.state() ? "enabled" : "disabled"
        );
    }

    public static void requestSwimSet(
            DAI_ActionCore action
    ) {

        DAI_MoveController.setSwim(
                action.state()
        );
    }

    public static void requestTargetClear(
            DAI_ActionCore action
    ) {

        DAI_TargetController.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared selected target."
        );
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

    private static void setLook(
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

            case "stop" ->
                    DAI_MoveController.stop();

            default ->
                    DAI_Core.LOGGER.warn(
                            "<DAI>: Unknown movement direction '{}'.",
                            direction
                    );
        }
    }

    public static void requestSwimToggle(
            DAI_ActionCore action
    ) {

        swimToggle();
    }

    private static void swimToggle() {

        DAI_Core.LOGGER.debug(
                "<DAI>: Toggling swim assist."
        );

        DAI_MoveController.toggleSwim();
    }

    public static void requestHotbarSelect(
            DAI_ActionCore action
    ) {

        DAI_HotbarController.select(
                action.slot()
        );
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

    public static void requestBreakOnce(
            DAI_ActionCore action
    ) {
        DAI_BreakController.breakOnce();
    }

    public static void requestBreakStart(
            DAI_ActionCore action
    ) {
        DAI_BreakController.start();
    }

    public static void requestBreakStop(
            DAI_ActionCore action
    ) {
        DAI_BreakController.stop();
    }

    public static void requestPlace(
            DAI_ActionCore action
    ) {

        DAI_BuildController.place();
    }

    public static void requestInteract(
            DAI_ActionCore action
    ) {

        DAI_ActionController.requestInteract();
    }

    public static void requestAddLook(
            DAI_ActionCore action
    ) {

        addLook(
                action.yaw(),
                action.pitch()
        );
    }

    private static void addLook(
            float yaw,
            float pitch
    ) {

        DAI_Core.LOGGER.debug(
                "<DAI>: Adding look rotation yaw={}, pitch={}.",
                yaw,
                pitch
        );

        DAI_InputController
                .look()
                .addRotation(
                        yaw,
                        pitch
                );
    }
    public static void requestAttackStart(
            DAI_ActionCore action
    ) {

        DAI_ActionController.startAttack();
    }

    public static void requestAttackStop(
            DAI_ActionCore action
    ) {

        DAI_ActionController.stopAttack();
    }

    public static void requestUseStart(
            DAI_ActionCore action
    ) {

        DAI_ActionController.startUse();
    }

    public static void requestUseStop(
            DAI_ActionCore action
    ) {

        DAI_ActionController.stopUse();
    }

    public static void requestInputStopAll(
            DAI_ActionCore action
    ) {

        DAI_MoveController.reset();
        DAI_ActionController.reset();

        DAI_Core.LOGGER.debug(
                "<DAI>: Stopped all managed input."
        );
    }

    public static void requestPickBlock(
            DAI_ActionCore action
    ) {

        DAI_KeyController.click(
                "pick_block"
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Requested pick block."
        );
    }

    public static void enqueueAction(
            DAI_ActionCore action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: enqueue_action requires an action id."
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Deferred enqueue of action '{}'.",
                action.action()
        );

        DAI_ActionExecutor.execute(
                action.action()
        );
    }

    public static void clearQueue(
            DAI_ActionCore action
    ) {

        DAI_ActionQueue.clear();

        DAI_Core.LOGGER.debug(
                "<DAI>: Cleared action queue."
        );
    }

    public static void requestRandomAction(
            DAI_ActionCore action
    ) {

        if (!action.hasSequence()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: random_action requires a non-empty sequence."
            );

            return;
        }

        List<DAI_ActionCore> sequence =
                action.sequence();

        DAI_ActionCore selected =
                sequence.get(
                        ThreadLocalRandom.current()
                                .nextInt(sequence.size())
                );

        DAI_Core.LOGGER.debug(
                "<DAI>: Randomly selected action type='{}'.",
                selected.type()
        );

        DAI_ActionQueue.enqueueFirst(
                selected
        );
    }

    public static void requestObjectiveExecute(
            DAI_ActionCore action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: objective_execute requires an objective id."
            );

            return;
        }

        Identifier objectiveId =
                parseObjectiveId(
                        action.action()
                );

        if (objectiveId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid objective id '{}'.",
                    action.action()
            );

            return;
        }

        String[] parts =
                objectiveId.getPath()
                        .split("/");

        String objectiveName =
                parts.length >= 2
                        ? parts[0]
                        + "_"
                        + parts[parts.length - 1]
                        : parts[0];

        String objectiveAction =
                DAI_Core.MODID
                        + ":"
                        + objectiveName;

        DAI_Core.LOGGER.debug(
                "<DAI>: Executing objective '{}' through flattened action '{}'.",
                objectiveId,
                objectiveAction
        );

        DAI_ActionExecutor.execute(
                objectiveAction
        );
    }

    public static void requestHotbarNext(
            DAI_ActionCore action
    ) {

        DAI_HotbarController.selectNext();
    }

    public static void requestHotbarPrevious(
            DAI_ActionCore action
    ) {

        DAI_HotbarController.selectPrevious();
    }

    public static void craftRecipe(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.getConnection() == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot craft without an active player and game mode."
            );

            return;
        }

        Identifier requestedResult =
                parseRecipeId(
                        action.action()
                );

        if (requestedResult == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: craft_recipe requires a valid output item id in 'action'."
            );

            return;
        }

        RecipeDisplayEntry selected =
                DAI_RecipeResolver.findByResult(
                        minecraft,
                        requestedResult
                );

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: No unlocked recipe display found for result '{}'.",
                    requestedResult
            );

            return;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        DAI_Core.LOGGER.debug(
                "<DAI>: Placing recipe result='{}', displayId={}, menu='{}', containerId={}, slots={}, max={}.",
                requestedResult,
                selected.id().index(),
                menu.getClass().getSimpleName(),
                menu.containerId,
                menu.slots.size(),
                action.state()
        );

        minecraft.gameMode.handlePlaceRecipe(
                menu.containerId,
                selected.id(),
                action.state()
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                3,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "craft_take_result",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                5,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }

    public static void craftTakeResult(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot take crafting result without an active player and game mode."
            );

            return;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        if (menu.slots.isEmpty()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot take crafting result because the current menu has no slots."
            );

            return;
        }

        ItemStack result =
                menu.getSlot(0)
                        .getItem();

        if (result.isEmpty()) {

            int retriesRemaining =
                    action.ticks();

            if (retriesRemaining <= 0) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Crafting result did not become available."
                );

                return;
            }

            DAI_Core.LOGGER.debug(
                    "<DAI>: Crafting result is not ready; retries remaining={}.",
                    retriesRemaining
            );

            DAI_ActionQueue.enqueueFirstAll(
                    List.of(
                            new DAI_ActionCore(
                                    "delay",
                                    "",
                                    List.of(),
                                    List.of(),
                                    "",
                                    "",
                                    0.0F,
                                    0.0F,
                                    "",
                                    1,
                                    0,
                                    false,
                                    0.0D
                            ),
                            new DAI_ActionCore(
                                    "craft_take_result",
                                    "",
                                    List.of(),
                                    List.of(),
                                    "",
                                    "",
                                    0.0F,
                                    0.0F,
                                    "",
                                    retriesRemaining - 1,
                                    0,
                                    false,
                                    0.0D
                            )
                    )
            );

            return;
        }

        Identifier resultId =
                result.getItem()
                        .builtInRegistryHolder()
                        .key()
                        .identifier();

        int resultCount =
                result.getCount();

        minecraft.gameMode.handleContainerInput(
                menu.containerId,
                0,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Crafted and stored '{}', count={}.",
                resultId,
                resultCount
        );
    }

    private static Identifier parseObjectiveId(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    private static Identifier parseRecipeId(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    public static void requestHotbarSelectItem(
            DAI_ActionCore action
    ) {

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: hotbar_select_item requires an item id in 'action'."
            );

            return;
        }

        Identifier itemId =
                parseItemId(
                        action.action()
                );

        if (itemId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid hotbar item id '{}'.",
                    action.action()
            );

            return;
        }

        DAI_HotbarController.selectItem(
                itemId
        );
    }
    private static Identifier parseItemId(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {

            normalized =
                    "minecraft:"
                            + normalized;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    public static void requestEquipBestTool(
            DAI_ActionCore action
    ) {

        String toolType =
                normalize(
                        action.action()
                );

        if (toolType.isEmpty()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: equip_best_tool requires a tool type in 'action'."
            );

            return;
        }

        equipBestTool(
                toolType
        );
    }

    public static void requestEquipBestWeapon(
            DAI_ActionCore action
    ) {

        Identifier[] candidates =
                identifiers(
                        "minecraft:netherite_sword",
                        "minecraft:netherite_axe",
                        "minecraft:diamond_sword",
                        "minecraft:diamond_axe",
                        "minecraft:iron_sword",
                        "minecraft:iron_axe",
                        "minecraft:stone_sword",
                        "minecraft:stone_axe",
                        "minecraft:golden_sword",
                        "minecraft:golden_axe",
                        "minecraft:wooden_sword",
                        "minecraft:wooden_axe",
                        "minecraft:trident",
                        "minecraft:mace",
                        "minecraft:bow",
                        "minecraft:crossbow"
                );

        equipFirstAvailable(
                "best weapon",
                candidates
        );
    }

    public static void requestEquipBestFood(
            DAI_ActionCore action
    ) {

        Identifier[] candidates =
                identifiers(
                        "minecraft:enchanted_golden_apple",
                        "minecraft:golden_apple",
                        "minecraft:golden_carrot",
                        "minecraft:cooked_beef",
                        "minecraft:cooked_porkchop",
                        "minecraft:cooked_mutton",
                        "minecraft:cooked_chicken",
                        "minecraft:cooked_rabbit",
                        "minecraft:cooked_cod",
                        "minecraft:cooked_salmon",
                        "minecraft:rabbit_stew",
                        "minecraft:mushroom_stew",
                        "minecraft:suspicious_stew",
                        "minecraft:pumpkin_pie",
                        "minecraft:bread",
                        "minecraft:baked_potato",
                        "minecraft:apple",
                        "minecraft:carrot",
                        "minecraft:melon_slice",
                        "minecraft:sweet_berries",
                        "minecraft:glow_berries",
                        "minecraft:dried_kelp"
                );

        equipFirstAvailable(
                "best food",
                candidates
        );
    }

    public static void requestEquipBestBlock(
            DAI_ActionCore action
    ) {

        String requestedGroup =
                normalize(
                        action.action()
                );

        Identifier[] candidates =
                switch (requestedGroup) {

                    case "", "general", "building" -> identifiers(
                            "minecraft:cobblestone",
                            "minecraft:stone",
                            "minecraft:deepslate",
                            "minecraft:cobbled_deepslate",
                            "minecraft:dirt",
                            "minecraft:oak_planks",
                            "minecraft:spruce_planks",
                            "minecraft:birch_planks",
                            "minecraft:jungle_planks",
                            "minecraft:acacia_planks",
                            "minecraft:dark_oak_planks",
                            "minecraft:mangrove_planks",
                            "minecraft:cherry_planks",
                            "minecraft:pale_oak_planks",
                            "minecraft:bamboo_planks",
                            "minecraft:crimson_planks",
                            "minecraft:warped_planks"
                    );

                    case "wood" -> identifiers(
                            "minecraft:oak_planks",
                            "minecraft:spruce_planks",
                            "minecraft:birch_planks",
                            "minecraft:jungle_planks",
                            "minecraft:acacia_planks",
                            "minecraft:dark_oak_planks",
                            "minecraft:mangrove_planks",
                            "minecraft:cherry_planks",
                            "minecraft:pale_oak_planks",
                            "minecraft:bamboo_planks",
                            "minecraft:crimson_planks",
                            "minecraft:warped_planks"
                    );

                    case "stone" -> identifiers(
                            "minecraft:cobblestone",
                            "minecraft:stone",
                            "minecraft:cobbled_deepslate",
                            "minecraft:deepslate",
                            "minecraft:andesite",
                            "minecraft:diorite",
                            "minecraft:granite",
                            "minecraft:tuff"
                    );

                    case "dirt" -> identifiers(
                            "minecraft:dirt",
                            "minecraft:coarse_dirt",
                            "minecraft:rooted_dirt",
                            "minecraft:mud"
                    );

                    default -> {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: Unsupported block group '{}'. Expected general, building, wood, stone, or dirt.",
                                requestedGroup
                        );

                        yield null;
                    }
                };

        if (candidates == null) {
            return;
        }

        equipFirstAvailable(
                requestedGroup.isEmpty()
                        ? "best building block"
                        : "best " + requestedGroup + " block",
                candidates
        );
    }
    private static void equipFirstAvailable(
            String description,
            Identifier[] candidates
    ) {

        for (Identifier candidate : candidates) {

            if (
                    DAI_HotbarController.selectItem(
                            candidate
                    )
            ) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Equipped {} '{}'.",
                        description,
                        candidate
                );

                return;
            }
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Could not equip {}; no matching item was found.",
                description
        );
    }

    private static Identifier[] identifiers(
            String... values
    ) {

        Identifier[] identifiers =
                new Identifier[values.length];

        for (
                int index = 0;
                index < values.length;
                index++
        ) {

            Identifier identifier =
                    Identifier.tryParse(
                            values[index]
                    );

            if (identifier == null) {

                throw new IllegalArgumentException(
                        "Invalid built-in identifier: "
                                + values[index]
                );
            }

            identifiers[index] =
                    identifier;
        }

        return identifiers;
    }

    public static void requestEquipBestToolForBlock(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot equip a tool for the targeted block without an active player and level."
            );

            return;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot equip a tool because no block is targeted."
            );

            return;
        }

        BlockState blockState =
                minecraft.level.getBlockState(
                        blockHitResult.getBlockPos()
                );

        String toolType =
                determineToolType(
                        blockState
                );

        if (toolType.isEmpty()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Targeted block '{}' does not require a supported tool.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier()
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Targeted block '{}' selected tool type '{}'.",
                blockState.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .identifier(),
                toolType
        );

        equipBestTool(
                toolType
        );
    }
    private static void equipBestTool(
            String toolType
    ) {

        Identifier[] candidates =
                switch (toolType) {

                    case "pickaxe" -> identifiers(
                            "minecraft:netherite_pickaxe",
                            "minecraft:diamond_pickaxe",
                            "minecraft:iron_pickaxe",
                            "minecraft:stone_pickaxe",
                            "minecraft:golden_pickaxe",
                            "minecraft:wooden_pickaxe"
                    );

                    case "axe" -> identifiers(
                            "minecraft:netherite_axe",
                            "minecraft:diamond_axe",
                            "minecraft:iron_axe",
                            "minecraft:stone_axe",
                            "minecraft:golden_axe",
                            "minecraft:wooden_axe"
                    );

                    case "shovel" -> identifiers(
                            "minecraft:netherite_shovel",
                            "minecraft:diamond_shovel",
                            "minecraft:iron_shovel",
                            "minecraft:stone_shovel",
                            "minecraft:golden_shovel",
                            "minecraft:wooden_shovel"
                    );

                    case "hoe" -> identifiers(
                            "minecraft:netherite_hoe",
                            "minecraft:diamond_hoe",
                            "minecraft:iron_hoe",
                            "minecraft:stone_hoe",
                            "minecraft:golden_hoe",
                            "minecraft:wooden_hoe"
                    );

                    default -> null;
                };

        if (candidates == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Unsupported tool type '{}'.",
                    toolType
            );

            return;
        }

        equipFirstAvailable(
                "best " + toolType,
                candidates
        );
    }
    private static String determineToolType(
            BlockState blockState
    ) {

        if (blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "pickaxe";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "axe";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "shovel";
        }

        if (blockState.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "hoe";
        }

        return "";
    }
    public static void requestMineTargetedBlock(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot mine without an active player and level."
            );

            return;
        }

        BlockPos selectedBlock =
                DAI_TargetController.selectedBlock();

        if (selectedBlock == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot mine because no block target is selected."
            );

            return;
        }

        BlockState blockState =
                minecraft.level.getBlockState(
                        selectedBlock
                );

        if (blockState.isAir()) {

            DAI_TargetController.clearBlock();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected block {} is already gone.",
                    selectedBlock
            );

            return;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult blockHitResult
                )
                        || !selectedBlock.equals(
                        blockHitResult.getBlockPos()
                )
        ) {

            int retriesRemaining =
                    action.ticks() > 0
                            ? action.ticks()
                            : 20;

            if (retriesRemaining <= 1) {

                DAI_Core.LOGGER.warn(
                        "<DAI>: Could not align with selected block {} for mining.",
                        selectedBlock
                );

                return;
            }

            DAI_ApproachController.faceSelectedBlock();

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mining target {} is not under the crosshair; retrying.",
                    selectedBlock
            );

            DAI_ActionQueue.enqueueFirstAll(
                    List.of(
                            new DAI_ActionCore(
                                    "delay",
                                    "",
                                    List.of(),
                                    List.of(),
                                    "",
                                    "",
                                    0.0F,
                                    0.0F,
                                    "",
                                    1,
                                    0,
                                    false,
                                    0.0D
                            ),
                            new DAI_ActionCore(
                                    "mine_targeted_block",
                                    "",
                                    List.of(),
                                    List.of(),
                                    "",
                                    "",
                                    0.0F,
                                    0.0F,
                                    "",
                                    retriesRemaining - 1,
                                    0,
                                    false,
                                    0.0D
                            )
                    )
            );

            return;
        }

        String toolType =
                determineToolType(
                        blockState
                );

        if (!toolType.isEmpty()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mining selected block '{}' at {} with preferred tool type '{}'.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock,
                    toolType
            );

            equipBestTool(
                    toolType
            );

        } else {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Mining selected block '{}' at {} with the current item.",
                    blockState.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selectedBlock
            );
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                2,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "break_once",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }
    public static void requestPlaceTargetedBlock(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot place a block without an active player, level, and game mode."
            );

            return;
        }

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: place_targeted_block requires a block item id in 'action'."
            );

            return;
        }

        Identifier blockItemId =
                parseItemId(
                        action.action()
                );

        if (blockItemId == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid block item id '{}'.",
                    action.action()
            );

            return;
        }

        Item item =
                BuiltInRegistries.ITEM.getValue(
                        blockItemId
                );

        if (
                item == null
                        || item == Items.AIR
                        || !(item instanceof BlockItem)
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Item '{}' is not a placeable block item.",
                    blockItemId
            );

            return;
        }

        if (
                !(
                        minecraft.hitResult
                                instanceof BlockHitResult
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot place '{}' because no block face is targeted.",
                    blockItemId
            );

            return;
        }

        if (
                !DAI_HotbarController.selectItem(
                        blockItemId
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot place '{}' because it is not available in the player inventory.",
                    blockItemId
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Preparing to place block item '{}'.",
                blockItemId
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                2,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "interact",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }
    public static void requestApproachTargetBlock(
            DAI_ActionCore action
    ) {

        double stopDistance =
                action.value() > 0.0D
                        ? action.value()
                        : 3.25D;

        int timeoutTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : 200;

        DAI_ApproachController.startSelectedBlock(
                stopDistance,
                timeoutTicks
        );
    }

    public static void requestRecognizeBlock(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot recognize a block without an active player and level."
            );

            return;
        }

        if (!action.hasAction()) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: recognize_block requires a block id or block tag in 'action'."
            );

            return;
        }

        String requestedTarget =
                action.action()
                        .trim();

        int searchRadius =
                action.value() > 0.0D
                        ? Mth.clamp(
                        (int) Math.round(
                                action.value()
                        ),
                        1,
                        32
                )
                        : 16;

        Predicate<BlockState> matcher =
                createBlockMatcher(
                        requestedTarget
                );

        if (matcher == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Invalid recognize_block target '{}'.",
                    requestedTarget
            );

            return;
        }

        BlockPos playerPos =
                minecraft.player
                        .blockPosition();

        BlockPos nearestPos =
                null;

        double nearestDistanceSquared =
                Double.MAX_VALUE;

        BlockPos minimum =
                playerPos.offset(
                        -searchRadius,
                        -searchRadius,
                        -searchRadius
                );

        BlockPos maximum =
                playerPos.offset(
                        searchRadius,
                        searchRadius,
                        searchRadius
                );

        for (
                BlockPos candidate
                : BlockPos.betweenClosed(
                minimum,
                maximum
        )
        ) {

            BlockState state =
                    minecraft.level.getBlockState(
                            candidate
                    );

            if (
                    state.isAir()
                            || !matcher.test(state)
            ) {
                continue;
            }

            double distanceSquared =
                    candidate.distSqr(
                            playerPos
                    );

            if (
                    distanceSquared
                            >= nearestDistanceSquared
            ) {
                continue;
            }

            nearestDistanceSquared =
                    distanceSquared;

            nearestPos =
                    candidate.immutable();
        }

        if (nearestPos == null) {

            DAI_TargetController.clearBlock();

            DAI_Core.LOGGER.debug(
                    "<DAI>: No block matching '{}' was found within {} block(s).",
                    requestedTarget,
                    searchRadius
            );

            return;
        }

        DAI_TargetController.selectBlock(
                nearestPos
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Recognized nearest block matching '{}' at {} (distance={}).",
                requestedTarget,
                nearestPos,
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        Math.sqrt(
                                nearestDistanceSquared
                        )
                )
        );
    }

    private static Predicate<BlockState> createBlockMatcher(
            String requestedTarget
    ) {

        if (
                requestedTarget == null
                        || requestedTarget.isBlank()
        ) {
            return null;
        }

        String normalized =
                requestedTarget.trim();

        if (normalized.startsWith("#")) {

            Identifier tagId =
                    parseBlockIdentifier(
                            normalized.substring(1)
                    );

            if (tagId == null) {
                return null;
            }

            TagKey<Block> tag =
                    TagKey.create(
                            Registries.BLOCK,
                            tagId
                    );

            return state -> state.is(tag);
        }

        Identifier blockId =
                parseBlockIdentifier(
                        normalized
                );

        if (blockId == null) {
            return null;
        }

        Block block =
                BuiltInRegistries.BLOCK.getValue(
                        blockId
                );

        if (
                block == null
                        || block == Blocks.AIR
        ) {
            return null;
        }

        return state -> state.is(block);
    }

    private static Identifier parseBlockIdentifier(
            String value
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value.trim();

        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }

        return Identifier.tryParse(normalized);
    }
    public static void requestWaitForApproach(
            DAI_ActionCore action
    ) {

        if (!DAI_ApproachController.isActive()) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Approach controller finished."
            );

            return;
        }

        int checksRemaining =
                action.ticks() > 0
                        ? action.ticks()
                        : 200;

        if (checksRemaining <= 1) {

            DAI_ApproachController.stop();

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out waiting for approach completion."
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                1,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_approach",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                checksRemaining - 1,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }
    public static void requestWaitForTargetBlock(
            DAI_ActionCore action
    ) {

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for block alignment because no block is selected."
            );

            return;
        }

        DAI_ApproachController.faceSelectedBlock();

        if (
                DAI_ApproachController
                        .isLookingAtSelectedBlock()
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Camera aligned with selected block {}.",
                    selected
            );

            return;
        }

        int checksRemaining =
                action.ticks() > 0
                        ? action.ticks()
                        : 20;

        if (checksRemaining <= 1) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out aligning with selected block {}.",
                    selected
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                1,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                checksRemaining - 1,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }
    public static void requestOpenContainer(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot open a container without an active player, level, and game mode."
            );

            return;
        }

        HitResult hitResult =
                minecraft.hitResult;

        if (
                !(
                        hitResult instanceof BlockHitResult
                                || hitResult instanceof EntityHitResult
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Cannot open a container because no block or entity is targeted."
            );

            return;
        }

        int retries =
                action.ticks() > 0
                        ? action.ticks()
                        : 20;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "interact",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                2,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_container",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                retries,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued container interaction."
        );
    }
    public static void requestWaitForContainer(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot wait for a container without an active player."
            );

            return;
        }

        if (
                !(
                        minecraft.player.containerMenu
                                instanceof InventoryMenu
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Container menu opened: {}.",
                    minecraft.player
                            .containerMenu
                            .getClass()
                            .getSimpleName()
            );

            return;
        }

        int retriesRemaining =
                action.ticks();

        if (retriesRemaining <= 1) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Timed out waiting for a container menu to open."
            );

            return;
        }

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "delay",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                1,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "wait_for_container",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                retriesRemaining - 1,
                                0,
                                false,
                                0.0D
                        )
                )
        );
    }

    public static void requestHarvestCrop(
            DAI_ActionCore action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot harvest a crop without an active player and level."
            );

            return;
        }

        BlockPos selected =
                DAI_TargetController.selectedBlock();

        if (selected == null) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: harvest_crop requires a selected block target."
            );

            return;
        }

        BlockState state =
                minecraft.level.getBlockState(
                        selected
                );

        if (!isHarvestableCrop(state)) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Selected block '{}' at {} is not a mature supported crop.",
                    state.getBlock()
                            .builtInRegistryHolder()
                            .key()
                            .identifier(),
                    selected
            );

            return;
        }

        DAI_ApproachController.faceSelectedBlock();

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        new DAI_ActionCore(
                                "wait_for_target_block",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                20,
                                0,
                                false,
                                0.0D
                        ),
                        new DAI_ActionCore(
                                "break_once",
                                "",
                                List.of(),
                                List.of(),
                                "",
                                "",
                                0.0F,
                                0.0F,
                                "",
                                0,
                                0,
                                false,
                                0.0D
                        )
                )
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Queued harvest for mature crop '{}' at {}.",
                state.getBlock()
                        .builtInRegistryHolder()
                        .key()
                        .identifier(),
                selected
        );
    }
    private static boolean isHarvestableCrop(
            BlockState state
    ) {

        if (
                state.getBlock()
                        instanceof CropBlock cropBlock
        ) {

            return cropBlock.isMaxAge(
                    state
            );
        }

        if (
                state.getBlock()
                        instanceof NetherWartBlock
        ) {

            return state.getValue(
                    NetherWartBlock.AGE
            ) >= 3;
        }

        if (
                state.getBlock()
                        instanceof CocoaBlock
        ) {

            return state.getValue(
                    CocoaBlock.AGE
            ) >= 2;
        }

        return state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON);
    }
    private static boolean hasLineOfSightToBlock(
            Minecraft minecraft,
            BlockPos blockPos
    ) {

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {
            return false;
        }

        Vec3 start =
                minecraft.player
                        .getEyePosition();

        Vec3 end =
                Vec3.atCenterOf(
                        blockPos
                );

        BlockHitResult result =
                minecraft.level.clip(
                        new ClipContext(
                                start,
                                end,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                minecraft.player
                        )
                );

        return result.getType() == HitResult.Type.BLOCK
                && result.getBlockPos().equals(
                blockPos
        );
    }
}