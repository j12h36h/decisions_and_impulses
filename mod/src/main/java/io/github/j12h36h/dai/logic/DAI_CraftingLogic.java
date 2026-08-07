package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.action.DAI_ActionQueue;
import io.github.j12h36h.dai.action.DAI_ActionResult;
import io.github.j12h36h.dai.action.DAI_ActionStatus;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.List;

public final class DAI_CraftingLogic {

    private static final int RESULT_DELAY_TICKS =
            3;

    private static final int RESULT_RETRY_COUNT =
            10;

    private DAI_CraftingLogic() {
        // Utility class.
    }

    /**
     * Attempts to place a recipe whose displayed result matches the
     * requested output into the player's current crafting menu.
     *
     * The requested output item is supplied through action.action().
     *
     * action.state():
     * false = craft once
     * true  = craft the maximum available amount
     */
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

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot craft without an active player, connection, and game mode."
            );

            return;
        }

        if (action == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot craft from a null action."
            );

            return;
        }

        Identifier requestedResult =
                parseResultId(
                        action.action()
                );

        if (requestedResult == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: craft_recipe requires a valid output item id in 'action'."
            );

            return;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        if (menu == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot craft '{}' because no container menu is available.",
                    requestedResult
            );

            return;
        }

        RecipeDisplayEntry recipe =
                findRecipeByResult(
                        minecraft,
                        requestedResult
                );

        if (recipe == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: No recipe display was found for '{}'.",
                    requestedResult
            );

            return;
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Placing recipe result='{}', displayId={}, menu='{}', containerId={}, craftMax={}.",
                requestedResult,
                recipe.id().index(),
                menu.getClass().getSimpleName(),
                menu.containerId,
                action.state()
        );

        minecraft.gameMode.handlePlaceRecipe(
                menu.containerId,
                recipe.id(),
                action.state()
        );

        /*
         * Allow the server time to place the ingredients and update
         * the crafting-result slot before attempting to collect it.
         */
        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createDelayAction(
                                RESULT_DELAY_TICKS
                        ),
                        createTakeResultAction(
                                RESULT_RETRY_COUNT
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );
    }

    /**
     * Attempts to quick-move the result from slot zero of the current
     * crafting menu into the player's inventory.
     *
     * If the server has not populated the result slot yet, this action
     * schedules another short delay and retries until its retry count
     * is exhausted.
     */
    public static void takeResult(
            DAI_ActionDefinition action
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot collect a crafting result without an active player and game mode."
            );

            return;
        }

        AbstractContainerMenu menu =
                minecraft.player.containerMenu;

        if (
                menu == null
                        || menu.slots.isEmpty()
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot collect a crafting result because the current menu has no slots."
            );

            return;
        }

        ItemStack result =
                menu.getSlot(0)
                        .getItem();

        if (result.isEmpty()) {

            retryTakeResult(
                    action
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

        DAI_ActionStatus.set(
                DAI_ActionResult.SUCCESS
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Collected crafting result '{}' x{} from menu='{}', containerId={}.",
                resultId,
                resultCount,
                menu.getClass().getSimpleName(),
                menu.containerId
        );
    }

    private static void retryTakeResult(
            DAI_ActionDefinition action
    ) {

        int retriesRemaining =
                action != null
                        ? action.ticks()
                        : 0;

        if (retriesRemaining <= 0) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.TIMED_OUT
            );

            DAI_Core.LOGGER.warn(
                    "<DAI>: Crafting result did not become available before the retry limit was reached."
            );

            return;
        }

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Crafting result is not ready; retries remaining={}.",
                retriesRemaining
        );

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createDelayAction(
                                1
                        ),
                        createTakeResultAction(
                                retriesRemaining - 1
                        )
                )
        );
    }

    /**
     * Finds a recipe display whose result matches the requested item.
     *
     * DAI intentionally does not use RecipeCollection#isCraftable here.
     * That flag is maintained by the vanilla recipe-book UI and can lag
     * behind inventory/advancement updates. The actual placement request is
     * sent to Minecraft, which remains responsible for accepting or rejecting
     * the recipe in the current menu.
     */
    private static RecipeDisplayEntry findRecipeByResult(
            Minecraft minecraft,
            Identifier requestedResult
    ) {

        if (minecraft.player == null) {
            return null;
        }

        ClientRecipeBook recipeBook =
                minecraft.player
                        .getRecipeBook();

        for (
                RecipeCollection collection
                : recipeBook.getCollections()
        ) {

            for (
                    RecipeDisplayEntry entry
                    : collection.getRecipes()
            ) {

                ItemStack result =
                        resolveRecipeResult(
                                entry
                        );

                if (result.isEmpty()) {
                    continue;
                }

                Identifier resultId =
                        result.getItem()
                                .builtInRegistryHolder()
                                .key()
                                .identifier();

                if (!requestedResult.equals(resultId)) {
                    continue;
                }

                DAI_Core.LOGGER.debug(
                        "<DAI>: Found recipe display for '{}' without requiring recipe-book craftable state.",
                        requestedResult
                );

                return entry;
            }
        }

        return null;
    }

    /**
     * Converts the supported recipe-display result forms into an
     * ItemStack that can be compared against the requested output.
     */
    private static ItemStack resolveRecipeResult(
            RecipeDisplayEntry entry
    ) {

        if (entry == null) {
            return ItemStack.EMPTY;
        }

        SlotDisplay resultDisplay =
                entry.display()
                        .result();

        if (
                resultDisplay
                        instanceof SlotDisplay.ItemSlotDisplay itemDisplay
        ) {

            return new ItemStack(
                    itemDisplay.item()
                            .value()
            );
        }

        if (
                resultDisplay
                        instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay
        ) {

            return stackDisplay.stack()
                    .create();
        }

        return ItemStack.EMPTY;
    }

    private static Identifier parseResultId(
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

    private static DAI_ActionDefinition createDelayAction(
            int ticks
    ) {

        return new DAI_ActionDefinition(
                "delay",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                ticks,
                0,
                false,
                0.0D
        );
    }

    private static DAI_ActionDefinition createTakeResultAction(
            int retries
    ) {

        return new DAI_ActionDefinition(
                "craft_take_result",
                "",
                List.of(),
                List.of(),
                "",
                "",
                0.0F,
                0.0F,
                "",
                Math.max(
                        0,
                        retries
                ),
                0,
                false,
                0.0D
        );
    }
}