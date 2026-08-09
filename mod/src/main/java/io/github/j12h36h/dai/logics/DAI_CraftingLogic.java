package io.github.j12h36h.dai.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.creation.DAI_CraftingState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.List;

public final class DAI_CraftingLogic {
    private static final int RESULT_DELAY_TICKS =
            3;
    private static final int RESULT_RETRY_COUNT =
            40;
    private static final int POLLS_PER_RECIPE =
            4;
    private DAI_CraftingLogic() {
        // Utility class.
    }
    public static void craftRecipe(
            DAI_ActionDefinition action
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();
        if (
                minecraft.player == null
                        || minecraft.getConnection() == null
                        || minecraft.gameMode == null
        ) {
            failPendingCraft(
                    "Cannot craft without an active player, connection, and game mode."
            );
            return;
        }
        if (action == null) {
            failPendingCraft(
                    "Cannot craft from a null action."
            );
            return;
        }
        Identifier requestedResult =
                DAI_CraftingMenuLogic.parseResultId(
                        action.action()
                );
        if (requestedResult == null) {
            failPendingCraft(
                    "craft_recipe requires a valid output item id in 'action'."
            );
            return;
        }
        AbstractContainerMenu menu =
                minecraft.player.containerMenu;
        if (
                menu == null
                        || menu.slots.isEmpty()
        ) {
            failPendingCraft(
                    "Cannot craft because no usable container menu is available."
            );
            return;
        }
        if (
                !DAI_CraftingMenuLogic.isCraftingMenu(
                        menu
                )
        ) {
            failPendingCraft(
                    "Cannot craft '"
                            + requestedResult
                            + "' because the active menu '"
                            + menu.getClass().getSimpleName()
                            + "' is not a supported crafting menu."
            );
            return;
        }
        if (DAI_CraftingState.isPending()) {
            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );
            DAI_Core.LOGGER.warn(
                    "<DAI>: Refused craft '{}' because result '{}' is still pending in containerId={}.",
                    requestedResult,
                    DAI_CraftingState.result(),
                    DAI_CraftingState.containerId()
            );
            return;
        }
        DAI_Core.LOGGER.debug(
                "<DAI>: Resolving craft_recipe result='{}' in menu='{}', containerId={}.",
                requestedResult,
                menu.getClass().getSimpleName(),
                menu.containerId
        );
        List<RecipeDisplayEntry> recipes =
                DAI_CraftingRecipeLogic.findRecipesByResult(
                        minecraft,
                        requestedResult
                );
        if (recipes.isEmpty()) {
            DAI_CraftingFallbackLogic.Result fallbackResult =
                    DAI_CraftingFallbackLogic.tryPlace(
                            minecraft,
                            requestedResult
                    );
            if (
                    fallbackResult
                            == DAI_CraftingFallbackLogic.Result.PLACED
            ) {
                DAI_CraftingState.beginFallback(
                        menu.containerId,
                        requestedResult
                );
                DAI_ActionQueue.holdBarrier(
                        DAI_CraftingMenuLogic.createTakeResultAction(
                                RESULT_RETRY_COUNT,
                                DAI_CraftingState.containerId()
                        ),
                        RESULT_DELAY_TICKS
                );
                DAI_ActionStatus.set(
                        DAI_ActionResult.RUNNING
                );
                return;
            }
            failPendingCraft(
                    "No vanilla recipe display was found for '"
                            + requestedResult
                            + "' in menu '"
                            + menu.getClass().getSimpleName()
                            + "', and DAI fallback result was "
                            + fallbackResult
                            + "."
            );
            return;
        }
        DAI_CraftingState.begin(
                menu.containerId,
                requestedResult,
                recipes
        );
        DAI_Core.LOGGER.debug(
                "<DAI>: Found {} candidate recipe display(s) for '{}' in menu='{}', containerId={}.",
                DAI_CraftingState.recipes().size(),
                DAI_CraftingState.result(),
                menu.getClass().getSimpleName(),
                DAI_CraftingState.containerId()
        );
        if (
                !DAI_CraftingRecipeLogic.placeCurrentRecipe(
                        minecraft,
                        action.state()
                )
        ) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            failPendingCraft(
                    "Unable to place any candidate recipe for '"
                            + requestedResult
                            + "'."
            );
            return;
        }
        DAI_ActionQueue.holdBarrier(
                DAI_CraftingMenuLogic.createTakeResultAction(
                        RESULT_RETRY_COUNT,
                        DAI_CraftingState.containerId()
                ),
                RESULT_DELAY_TICKS
        );
        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }
    public static void takeResult(
            DAI_ActionDefinition action
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();
        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            failPendingCraft(
                    "Cannot collect a crafting result without an active player and game mode."
            );
            return;
        }
        if (!DAI_CraftingState.isPending()) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );
            DAI_Core.LOGGER.warn(
                    "<DAI>: craft_take_result executed without a pending craft operation."
            );
            return;
        }
        AbstractContainerMenu menu =
                minecraft.player.containerMenu;
        if (menu == null) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            failPendingCraft(
                    "Crafting menu disappeared while waiting for the result."
            );
            return;
        }
        if (
                menu.containerId
                        != DAI_CraftingState.containerId()
        ) {
            Identifier expectedResult =
                    DAI_CraftingState.result();
            int expectedContainerId =
                    DAI_CraftingState.containerId();
            int actualContainerId =
                    menu.containerId;
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            DAI_CraftingState.clear();
            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );
            DAI_Core.LOGGER.warn(
                    "<DAI>: Crafting menu changed while waiting for '{}': expected containerId={}, actual containerId={}.",
                    expectedResult,
                    expectedContainerId,
                    actualContainerId
            );
            return;
        }
        if (
                !DAI_CraftingMenuLogic.isCraftingMenu(
                        menu
                )
        ) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            failPendingCraft(
                    "The active menu stopped being a supported crafting menu while waiting for the result."
            );
            return;
        }
        if (menu.slots.isEmpty()) {
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            failPendingCraft(
                    "Crafting menu contained no result slot."
            );
            return;
        }
        ItemStack result =
                menu.getSlot(
                        0
                ).getItem();
        if (result.isEmpty()) {
            handleEmptyResult(
                    minecraft,
                    action
            );
            return;
        }
        Identifier resultId =
                DAI_CraftingMenuLogic.itemId(
                        result
                );
        if (
                DAI_CraftingState.result() != null
                        && !DAI_CraftingState.result().equals(
                        resultId
                )
        ) {
            Identifier expected =
                    DAI_CraftingState.result();
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            DAI_CraftingState.clear();
            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );
            DAI_Core.LOGGER.warn(
                    "<DAI>: Crafting result mismatch: expected '{}', found '{}'.",
                    expected,
                    resultId
            );
            return;
        }
        int resultCount =
                result.getCount();
        int completedContainerId =
                DAI_CraftingState.containerId();
        Identifier completedResult =
                DAI_CraftingState.result();
        minecraft.gameMode.handleContainerInput(
                completedContainerId,
                0,
                0,
                ContainerInput.QUICK_MOVE,
                minecraft.player
        );
        DAI_CraftingState.clear();
        DAI_CraftingMenuLogic.releaseCraftBarrier();
        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );
        DAI_Core.LOGGER.info(
                "<DAI>: Crafted '{}' x{} successfully in menu='{}', containerId={}.",
                completedResult,
                resultCount,
                menu.getClass().getSimpleName(),
                completedContainerId
        );
    }
    private static void handleEmptyResult(
            Minecraft minecraft,
            DAI_ActionDefinition action
    ) {
        int retriesRemaining =
                action != null
                        ? action.ticks()
                        : 0;
        if (retriesRemaining <= 0) {
            Identifier failedResult =
                    DAI_CraftingState.result();
            int failedContainerId =
                    DAI_CraftingState.containerId();
            boolean fallback =
                    DAI_CraftingState.isFallback();
            int attemptedRecipes =
                    DAI_CraftingState.recipeIndex() + 1;
            int candidateCount =
                    DAI_CraftingState.recipes().size();
            DAI_CraftingState.clear();
            DAI_CraftingMenuLogic.releaseCraftBarrier();
            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );
            if (fallback) {
                DAI_Core.LOGGER.warn(
                        "<DAI>: DAI fallback crafting result '{}' did not become available in containerId={} before timeout.",
                        failedResult,
                        failedContainerId
                );
            } else {
                DAI_Core.LOGGER.warn(
                        "<DAI>: Crafting result '{}' did not become available in containerId={} after trying {}/{} candidate recipe display(s).",
                        failedResult,
                        failedContainerId,
                        attemptedRecipes,
                        candidateCount
                );
            }
            return;
        }
        if (DAI_CraftingState.isFallback()) {
            DAI_ActionQueue.holdBarrier(
                    DAI_CraftingMenuLogic.createTakeResultAction(
                            retriesRemaining - 1,
                            DAI_CraftingState.containerId()
                    ),
                    1
            );
            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );
            return;
        }
        DAI_CraftingState.incrementRecipePolls();
        if (
                DAI_CraftingState.recipePolls()
                        >= POLLS_PER_RECIPE
                        && DAI_CraftingRecipeLogic.hasNextRecipe()
        ) {
            DAI_CraftingState.advanceRecipe();
            DAI_Core.LOGGER.debug(
                    "<DAI>: Candidate recipe {}/{} for '{}' produced no output; trying candidate {}.",
                    DAI_CraftingState.recipeIndex(),
                    DAI_CraftingState.recipes().size(),
                    DAI_CraftingState.result(),
                    DAI_CraftingState.recipeIndex() + 1
            );
            if (
                    !DAI_CraftingRecipeLogic.placeCurrentRecipe(
                            minecraft,
                            false
                    )
            ) {
                DAI_CraftingMenuLogic.releaseCraftBarrier();
                failPendingCraft(
                        "Failed while attempting another recipe candidate for '"
                                + DAI_CraftingState.result()
                                + "'."
                );
                return;
            }
            DAI_ActionQueue.holdBarrier(
                    DAI_CraftingMenuLogic.createTakeResultAction(
                            retriesRemaining - 1,
                            DAI_CraftingState.containerId()
                    ),
                    RESULT_DELAY_TICKS
            );
            DAI_ActionStatus.set(
                    DAI_ActionResult.RUNNING
            );
            return;
        }
        DAI_ActionQueue.holdBarrier(
                DAI_CraftingMenuLogic.createTakeResultAction(
                        retriesRemaining - 1,
                        DAI_CraftingState.containerId()
                ),
                1
        );
        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }
    private static void failPendingCraft(
            String reason
    ) {
        DAI_CraftingState.clear();
        DAI_ActionStatus.set(
                DAI_ActionResult.FAILURE
        );
        DAI_Core.LOGGER.warn(
                "<DAI>: {}",
                reason
        );
    }
    public static void reset() {
        DAI_CraftingState.clear();
        DAI_CraftingMenuLogic.releaseCraftBarrier();
    }
    public static boolean isCraftPending() {
        return DAI_CraftingState.isPending();
    }
    public static Identifier pendingResult() {
        return DAI_CraftingState.result();
    }
    public static int pendingContainerId() {
        return DAI_CraftingState.containerId();
    }
}
