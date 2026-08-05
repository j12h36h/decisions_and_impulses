package io.github.j12h36h.dai.condition;

import net.minecraft.world.item.ItemStack;

public final class DAI_ConditionsItem {

    private DAI_ConditionsItem() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "inventory_item_count",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    String expectedItemId =
                            normalize(
                                    condition.stringValue()
                            );

                    if (expectedItemId.isEmpty()) {
                        return DAI_ConditionValue.missing();
                    }

                    int count = 0;

                    for (
                            int slot = 0;
                            slot < context.player()
                                    .getInventory()
                                    .getContainerSize();
                            slot++
                    ) {

                        ItemStack stack =
                                context.player()
                                        .getInventory()
                                        .getItem(slot);

                        if (
                                !stack.isEmpty()
                                        && expectedItemId.equals(
                                        itemId(stack)
                                )
                        ) {
                            count += stack.getCount();
                        }
                    }

                    return DAI_ConditionValue.number(
                            count
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "mainhand_stack_size",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getMainHandItem()
                                    .getCount()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_stack_size",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getOffhandItem()
                                    .getCount()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "mainhand_damage",
                (context, condition) -> {

                    ItemStack stack =
                            mainHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getDamageValue()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "mainhand_max_damage",
                (context, condition) -> {

                    ItemStack stack =
                            mainHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getMaxDamage()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "mainhand_durability",
                (context, condition) -> {

                    ItemStack stack =
                            mainHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getMaxDamage()
                                    - stack.getDamageValue()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_damage",
                (context, condition) -> {

                    ItemStack stack =
                            offHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getDamageValue()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_max_damage",
                (context, condition) -> {

                    ItemStack stack =
                            offHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getMaxDamage()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_durability",
                (context, condition) -> {

                    ItemStack stack =
                            offHand(context);

                    if (
                            stack == null
                                    || stack.isEmpty()
                                    || !stack.isDamageableItem()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            stack.getMaxDamage()
                                    - stack.getDamageValue()
                    );
                }
        );
    }

    private static ItemStack mainHand(
            DAI_ConditionContext context
    ) {

        if (!context.hasPlayer()) {
            return null;
        }

        return context.player()
                .getMainHandItem();
    }

    private static ItemStack offHand(
            DAI_ConditionContext context
    ) {

        if (!context.hasPlayer()) {
            return null;
        }

        return context.player()
                .getOffhandItem();
    }

    private static String itemId(
            ItemStack stack
    ) {

        if (stack == null || stack.isEmpty()) {
            return "";
        }

        return stack.getItem()
                .builtInRegistryHolder()
                .key()
                .identifier()
                .toString();
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase();
    }
}
