package io.github.j12h36h.dai.logics.condition;

import io.github.j12h36h.dai.menus.system.DAI_RecipeResolver;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DAI_ConditionsInventory {

    private DAI_ConditionsInventory() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "holding_item",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    ItemStack stack =
                            context.player()
                                    .getMainHandItem();

                    return DAI_ConditionValue.string(
                            stack.isEmpty()
                                    ? ""
                                    : stack.getItem()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_item",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    ItemStack stack =
                            context.player()
                                    .getOffhandItem();

                    return DAI_ConditionValue.string(
                            stack.isEmpty()
                                    ? ""
                                    : stack.getItem()
                                    .builtInRegistryHolder()
                                    .key()
                                    .identifier()
                                    .toString()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "inventory_has_space",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    boolean hasSpace =
                            context.player()
                                    .getInventory()
                                    .getFreeSlot() >= 0;

                    return DAI_ConditionValue.bool(
                            hasSpace
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "selected_hotbar_slot",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getInventory()
                                    .getSelectedSlot() + 1
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "mainhand_empty",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getMainHandItem()
                                    .isEmpty()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_empty",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getOffhandItem()
                                    .isEmpty()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "container_has_item",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Identifier itemId =
                            parseIdentifier(
                                    condition.parameter()
                            );

                    if (itemId == null) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: container_has_item requires a valid item id."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    Item item =
                            BuiltInRegistries.ITEM.getValue(
                                    itemId
                            );

                    if (
                            item == null
                                    || item == Items.AIR
                    ) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: Unknown item '{}'.",
                                itemId
                        );

                        return DAI_ConditionValue.missing();
                    }

                    int count = 0;

                    var inventory =
                            context.player()
                                    .getInventory();

                    for (
                            int slot = 0;
                            slot < inventory.getContainerSize();
                            slot++
                    ) {

                        ItemStack stack =
                                inventory.getItem(
                                        slot
                                );

                        if (stack.is(item)) {
                            count += stack.getCount();
                        }
                    }

                    return DAI_ConditionValue.number(
                            count
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "recipe_unlocked",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    Identifier resultId =
                            parseIdentifier(
                                    condition.parameter()
                            );

                    if (resultId == null) {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: recipe_unlocked requires a valid recipe result item id."
                        );

                        return DAI_ConditionValue.missing();
                    }

                    Minecraft minecraft =
                            Minecraft.getInstance();

                    if (
                            minecraft.player == null
                                    || minecraft.getConnection() == null
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    boolean unlocked =
                            DAI_RecipeResolver.hasResult(
                                    minecraft,
                                    resultId
                            );

                    DAI_Core.debug(
                            "<DAI>: Recipe result '{}' unlocked={}.",
                            resultId,
                            unlocked
                    );

                    return DAI_ConditionValue.bool(
                            unlocked
                    );
                }
        );
    }

    private static Identifier parseIdentifier(
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
}