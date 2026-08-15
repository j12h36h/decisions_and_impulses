package io.github.j12h36h.dai.client.logics;

import io.github.j12h36h.dai.logics.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionQueue;
import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.client.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class DAI_FoodLogic {

    private static final int DEFAULT_USE_TICKS =
            40;

    private static final int HOTBAR_SWITCH_DELAY =
            2;

    private DAI_FoodLogic() {
        // Utility class.
    }

    public static void eatBestFood(
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
                    "<DAI>: Cannot eat without an active player and game mode."
            );

            return;
        }

        /*
         * Nothing needs to be done.
         * Treat this as a successful no-op rather than a failure.
         */
        if (
                minecraft.player
                        .getFoodData()
                        .getFoodLevel() >= 20
        ) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.SUCCESS
            );

            DAI_Core.debug(
                    "<DAI>: Player is not hungry; eat_best_food skipped."
            );

            return;
        }

        Identifier selectedFood =
                selectBestFood();

        if (selectedFood == null) {

            DAI_ActionStatus.set(
                    DAI_ActionResult.FAILURE
            );

            DAI_Core.debug(
                    "<DAI>: No supported food was found in the player inventory."
            );

            return;
        }

        int useTicks =
                action.ticks() > 0
                        ? action.ticks()
                        : DEFAULT_USE_TICKS;

        DAI_ActionQueue.enqueueFirstAll(
                List.of(
                        createAction(
                                "delay",
                                HOTBAR_SWITCH_DELAY
                        ),
                        createAction(
                                "use_start",
                                0
                        ),
                        createAction(
                                "delay",
                                useTicks
                        ),
                        createAction(
                                "use_stop",
                                0
                        )
                )
        );

        DAI_ActionStatus.set(
                DAI_ActionResult.RUNNING
        );

        DAI_Core.debug(
                "<DAI>: Queued eating '{}' for {} tick(s).",
                selectedFood,
                useTicks
        );
    }

    private static Identifier selectBestFood() {

        for (
                Identifier candidate
                : foodCandidates()
        ) {

            if (
                    DAI_HotbarLogic.selectItem(
                            candidate
                    )
            ) {
                return candidate;
            }
        }

        return null;
    }

    private static Identifier[] foodCandidates() {

        return identifiers(
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
                        "Invalid built-in food identifier: "
                                + values[index]
                );
            }

            identifiers[index] =
                    identifier;
        }

        return identifiers;
    }

    private static DAI_ActionDefinition createAction(
            String type,
            int ticks
    ) {

        return new DAI_ActionDefinition(
                type,
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
}