package io.github.j12h36h.dai.logic;

import io.github.j12h36h.dai.action.DAI_ActionDefinition;
import io.github.j12h36h.dai.core.DAI_Core;
import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class DAI_EquipmentLogic {

    private DAI_EquipmentLogic() {
        // Utility class.
    }

    public static void selectItem(
            DAI_ActionDefinition action
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

        if (
                !DAI_HotbarLogic.selectItem(
                        itemId
                )
        ) {

            DAI_Core.LOGGER.debug(
                    "<DAI>: Item '{}' was not found in the player's inventory.",
                    itemId
            );
        }
    }

    public static void equipBestTool(
            DAI_ActionDefinition action
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

        Identifier[] candidates =
                switch (toolType) {

                    case "pickaxe" ->
                            toolCandidates(
                                    "pickaxe"
                            );

                    case "axe" ->
                            toolCandidates(
                                    "axe"
                            );

                    case "shovel" ->
                            toolCandidates(
                                    "shovel"
                            );

                    case "hoe" ->
                            toolCandidates(
                                    "hoe"
                            );

                    case "sword" ->
                            toolCandidates(
                                    "sword"
                            );

                    default -> {

                        DAI_Core.LOGGER.warn(
                                "<DAI>: Unsupported tool type '{}'. Expected pickaxe, axe, shovel, hoe, or sword.",
                                toolType
                        );

                        yield null;
                    }
                };

        if (candidates == null) {
            return;
        }

        equipFirstAvailable(
                "best " + toolType,
                candidates
        );
    }

    public static void equipBestWeapon(
            DAI_ActionDefinition action
    ) {

        equipFirstAvailable(
                "best weapon",
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
                )
        );
    }

    public static void equipBestFood(
            DAI_ActionDefinition action
    ) {

        equipFirstAvailable(
                "best food",
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
                )
        );
    }

    public static void equipBestBlock(
            DAI_ActionDefinition action
    ) {

        String requestedGroup =
                normalize(
                        action.action()
                );

        Identifier[] candidates =
                switch (requestedGroup) {

                    case "", "general", "building" ->
                            identifiers(
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

                    case "wood" ->
                            identifiers(
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

                    case "stone" ->
                            identifiers(
                                    "minecraft:cobblestone",
                                    "minecraft:stone",
                                    "minecraft:cobbled_deepslate",
                                    "minecraft:deepslate",
                                    "minecraft:andesite",
                                    "minecraft:diorite",
                                    "minecraft:granite",
                                    "minecraft:tuff"
                            );

                    case "dirt" ->
                            identifiers(
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

    private static Identifier[] toolCandidates(
            String toolType
    ) {

        return identifiers(
                "minecraft:netherite_" + toolType,
                "minecraft:diamond_" + toolType,
                "minecraft:iron_" + toolType,
                "minecraft:stone_" + toolType,
                "minecraft:golden_" + toolType,
                "minecraft:wooden_" + toolType
        );
    }

    private static boolean equipFirstAvailable(
            String description,
            Identifier[] candidates
    ) {

        for (Identifier candidate : candidates) {

            if (
                    DAI_HotbarLogic.selectItem(
                            candidate
                    )
            ) {

                DAI_Core.LOGGER.debug(
                        "<DAI>: Equipped {} '{}'.",
                        description,
                        candidate
                );

                return true;
            }
        }

        DAI_Core.LOGGER.debug(
                "<DAI>: Could not equip {}; no matching item was found.",
                description
        );

        return false;
    }

    private static Identifier[] identifiers(
            String... values
    ) {

        Identifier[] identifiers =
                new Identifier[
                        values.length
                        ];

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
                        "Invalid built-in item identifier: "
                                + values[index]
                );
            }

            identifiers[index] =
                    identifier;
        }

        return identifiers;
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
}
