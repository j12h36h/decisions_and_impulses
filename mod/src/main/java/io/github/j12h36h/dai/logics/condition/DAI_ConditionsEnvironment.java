package io.github.j12h36h.dai.logics.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;

public final class DAI_ConditionsEnvironment {

    private DAI_ConditionsEnvironment() {
        // Utility class.
    }

    public static void registerAll() {

        DAI_ConditionRegistry.register(
                "biome",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return context.level()
                            .getBiome(
                                    context.player()
                                            .blockPosition()
                            )
                            .unwrapKey()
                            .map(key ->
                                    DAI_ConditionValue.string(
                                            key.identifier()
                                                    .toString()
                                    )
                            )
                            .orElseGet(
                                    DAI_ConditionValue::missing
                            );
                }
        );

        DAI_ConditionRegistry.register(
                "biome_tag",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    TagKey<Biome> tag =
                            resolveBiomeTag(
                                    condition.parameter()
                            );

                    if (tag == null) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .getBiome(
                                            context.player()
                                                    .blockPosition()
                                    )
                                    .is(tag)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "fluid_at_feet",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    FluidState state =
                            context.level()
                                    .getFluidState(
                                            context.player()
                                                    .blockPosition()
                                    );

                    return fluidId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "fluid_below",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    FluidState state =
                            context.level()
                                    .getFluidState(
                                            context.player()
                                                    .blockPosition()
                                                    .below()
                                    );

                    return fluidId(state);
                }
        );

        DAI_ConditionRegistry.register(
                "player_underwater",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isUnderWater()
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_in_rain",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    BlockPos position =
                            context.player()
                                    .blockPosition();

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .isRainingAt(position)
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "player_in_powder_snow",
                (context, condition) -> {

                    if (!context.hasPlayer()) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.player()
                                    .isInPowderSnow
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "underground",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            !context.level()
                                    .canSeeSky(
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "open_sky",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.bool(
                            context.level()
                                    .canSeeSky(
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "sky_light",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.level()
                                    .getBrightness(
                                            net.minecraft.world.level.LightLayer.SKY,
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );

        DAI_ConditionRegistry.register(
                "block_light",
                (context, condition) -> {

                    if (
                            !context.hasLevel()
                                    || !context.hasPlayer()
                    ) {
                        return DAI_ConditionValue.missing();
                    }

                    return DAI_ConditionValue.number(
                            context.level()
                                    .getBrightness(
                                            net.minecraft.world.level.LightLayer.BLOCK,
                                            context.player()
                                                    .blockPosition()
                                    )
                    );
                }
        );
    }

    private static TagKey<Biome> resolveBiomeTag(
            String value
    ) {

        Identifier id =
                parseIdentifier(value);

        if (id == null) {
            return null;
        }

        return TagKey.create(
                Registries.BIOME,
                id
        );
    }

    private static DAI_ConditionValue fluidId(
            FluidState state
    ) {

        if (state == null || state.isEmpty()) {
            return DAI_ConditionValue.string("");
        }

        return DAI_ConditionValue.string(
                state.getType()
                        .builtInRegistryHolder()
                        .key()
                        .identifier()
                        .toString()
        );
    }

    private static Identifier parseIdentifier(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase();

        if (normalized.startsWith("#")) {
            normalized =
                    normalized.substring(1);
        }

        return normalized.isEmpty()
                ? null
                : Identifier.tryParse(normalized);
    }
}
