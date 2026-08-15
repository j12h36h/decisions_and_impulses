package io.github.j12h36h.dai.client.logics.condition;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Locale;

public final class DAI_ConditionsTag {

    private DAI_ConditionsTag() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "holding_item_tag",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    TagKey<Item> tag =
                            resolveItemTag(
                                    condition.parameter()
                            );

                    if (tag == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getMainHandItem()
                                    .is(tag)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "offhand_item_tag",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    TagKey<Item> tag =
                            resolveItemTag(
                                    condition.parameter()
                            );

                    if (tag == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .getOffhandItem()
                                    .is(tag)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "inventory_item_tag_count",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    TagKey<Item> tag =
                            resolveItemTag(
                                    condition.parameter()
                            );

                    if (tag == null) {
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
                                        && stack.is(tag)
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
                "equipment_item_tag",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    EquipmentQuery query =
                            parseEquipmentQuery(
                                    condition.parameter()
                            );

                    if (query == null) {
                        return DAI_ConditionValue.missing();
                    }

                    EquipmentSlot slot =
                            resolveSlot(query.slot());

                    if (slot == null) {
                        return DAI_ConditionValue.missing();
                    }

                    ItemStack stack =
                            context.player()
                                    .getItemBySlot(slot);

                    if (stack == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            stack.is(
                                    query.tag()
                            )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "targeted_block_tag",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !(context.hitResult()
                                    instanceof BlockHitResult blockHitResult)
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    TagKey<Block> tag =
                            resolveBlockTag(
                                    condition.parameter()
                            );

                    if (tag == null) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockState state =
                            context.level()
                                    .getBlockState(
                                            blockHitResult.getBlockPos()
                                    );

                    return DAI_ConditionValue.bool(
                            state.is(tag)
                    );
                }
        );
    }

    private static TagKey<Item> resolveItemTag(
            String value
    ) {

        Identifier id =
                parseIdentifier(value);

        if (id == null) {
            return null;
        }

        return TagKey.create(
                Registries.ITEM,
                id
        );
    }

    private static TagKey<Block> resolveBlockTag(
            String value
    ) {

        Identifier id =
                parseIdentifier(value);

        if (id == null) {
            return null;
        }

        return TagKey.create(
                Registries.BLOCK,
                id
        );
    }

    private static Identifier parseIdentifier(
            String value
    ) {

        String normalized =
                normalize(value);

        if (normalized.startsWith("#")) {
            normalized =
                    normalized.substring(1);
        }

        if (normalized.isEmpty()) {
            return null;
        }

        return Identifier.tryParse(
                normalized
        );
    }

    private static EquipmentQuery parseEquipmentQuery(
            String value
    ) {

        String normalized =
                normalize(value);

        String[] parts =
                normalized.split(
                        "\\|",
                        2
                );

        if (parts.length != 2) {
            return null;
        }

        String slot =
                normalize(
                        parts[0]
                );

        TagKey<Item> tag =
                resolveItemTag(
                        parts[1]
                );

        if (
                slot.isEmpty()
                        || tag == null
        ) {
            return null;
        }

        return new EquipmentQuery(
                slot,
                tag
        );
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private record EquipmentQuery(
            String slot,
            TagKey<Item> tag
    ) {
    }

    private static EquipmentSlot resolveSlot(
            String value
    ) {

        return switch (normalize(value)) {

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
}
