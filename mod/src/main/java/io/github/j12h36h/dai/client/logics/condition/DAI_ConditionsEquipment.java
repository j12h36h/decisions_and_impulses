package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class DAI_ConditionsEquipment {

    private DAI_ConditionsEquipment() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "equipment_item",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    EquipmentSlot slot =
                            resolveSlot(
                                    condition.parameter()
                            );

                    if (slot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    ItemStack stack =
                            context.player()
                                    .getItemBySlot(slot);

                    return DAI_ConditionValue.string(
                            itemId(stack)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "equipment_empty",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    EquipmentSlot slot =
                            resolveSlot(
                                    condition.parameter()
                            );

                    if (slot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getItemBySlot(slot)
                                    .isEmpty()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "equipment_stack_size",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    EquipmentSlot slot =
                            resolveSlot(
                                    condition.parameter()
                            );

                    if (slot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.player()
                                    .getItemBySlot(slot)
                                    .getCount()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "equipment_durability",
                (context, condition) -> {

                    ItemStack stack =
                            equipmentStack(
                                    context,
                                    condition.parameter()
                            );

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
                "equipment_damage",
                (context, condition) -> {

                    ItemStack stack =
                            equipmentStack(
                                    context,
                                    condition.parameter()
                            );

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
                "equipment_max_damage",
                (context, condition) -> {

                    ItemStack stack =
                            equipmentStack(
                                    context,
                                    condition.parameter()
                            );

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
    }

    private static ItemStack equipmentStack(
            DAI_ConditionContext context,
            String slotName
    ) {

        if (!context.hasPlayer()) {
            return null;
        }

        EquipmentSlot slot =
                resolveSlot(slotName);

        if (slot == null) {
            return null;
        }

        return context.player()
                .getItemBySlot(slot);
    }

    private static EquipmentSlot resolveSlot(
            String value
    ) {

        String normalized =
                value == null
                        ? ""
                        : value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return switch (normalized) {

            case "mainhand", "main_hand", "hand" ->
                    EquipmentSlot.MAINHAND;

            case "offhand", "off_hand" ->
                    EquipmentSlot.OFFHAND;

            case "head", "helmet" ->
                    EquipmentSlot.HEAD;

            case "chest", "chestplate" ->
                    EquipmentSlot.CHEST;

            case "legs", "leggings" ->
                    EquipmentSlot.LEGS;

            case "feet", "boots" ->
                    EquipmentSlot.FEET;

            default ->
                    null;
        };
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
}
