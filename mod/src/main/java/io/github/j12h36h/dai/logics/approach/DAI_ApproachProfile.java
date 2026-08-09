package io.github.j12h36h.dai.logics.approach;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

public final class DAI_ApproachProfile {

    public enum Category {

        PASSIVE,
        NEUTRAL,
        HOSTILE_MELEE,
        HOSTILE_RANGED,
        VILLAGER,
        FLYING,
        AQUATIC,
        BOSS
    }

    private static final Set<String> VILLAGER_TYPES =
            Set.of(
                    "minecraft:villager",
                    "minecraft:wandering_trader"
            );

    private static final Set<String> FLYING_TYPES =
            Set.of(
                    "minecraft:allay",
                    "minecraft:bat",
                    "minecraft:bee",
                    "minecraft:blaze",
                    "minecraft:breeze",
                    "minecraft:ghast",
                    "minecraft:happy_ghast",
                    "minecraft:parrot",
                    "minecraft:phantom",
                    "minecraft:vex"
            );

    private static final Set<String> AQUATIC_TYPES =
            Set.of(
                    "minecraft:axolotl",
                    "minecraft:cod",
                    "minecraft:dolphin",
                    "minecraft:drowned",
                    "minecraft:elder_guardian",
                    "minecraft:glow_squid",
                    "minecraft:guardian",
                    "minecraft:pufferfish",
                    "minecraft:salmon",
                    "minecraft:squid",
                    "minecraft:tadpole",
                    "minecraft:tropical_fish"
            );

    private static final Set<String> BOSS_TYPES =
            Set.of(
                    "minecraft:ender_dragon",
                    "minecraft:wither",
                    "minecraft:warden"
            );

    private static final Set<String> RANGED_HOSTILE_TYPES =
            Set.of(
                    "minecraft:bogged",
                    "minecraft:breeze",
                    "minecraft:drowned",
                    "minecraft:evoker",
                    "minecraft:ghast",
                    "minecraft:illusioner",
                    "minecraft:pillager",
                    "minecraft:skeleton",
                    "minecraft:stray",
                    "minecraft:witch"
            );

    private static final Set<String> HOSTILE_TYPES =
            Set.of(
                    "minecraft:blaze",
                    "minecraft:bogged",
                    "minecraft:breeze",
                    "minecraft:cave_spider",
                    "minecraft:creaking",
                    "minecraft:creeper",
                    "minecraft:drowned",
                    "minecraft:elder_guardian",
                    "minecraft:ender_dragon",
                    "minecraft:endermite",
                    "minecraft:evoker",
                    "minecraft:ghast",
                    "minecraft:guardian",
                    "minecraft:hoglin",
                    "minecraft:husk",
                    "minecraft:illusioner",
                    "minecraft:magma_cube",
                    "minecraft:phantom",
                    "minecraft:piglin_brute",
                    "minecraft:pillager",
                    "minecraft:ravager",
                    "minecraft:shulker",
                    "minecraft:silverfish",
                    "minecraft:skeleton",
                    "minecraft:slime",
                    "minecraft:spider",
                    "minecraft:stray",
                    "minecraft:vex",
                    "minecraft:vindicator",
                    "minecraft:warden",
                    "minecraft:witch",
                    "minecraft:wither",
                    "minecraft:wither_skeleton",
                    "minecraft:zoglin",
                    "minecraft:zombie",
                    "minecraft:zombie_villager"
            );

    private static final Set<String> PASSIVE_TYPES =
            Set.of(
                    "minecraft:allay",
                    "minecraft:armadillo",
                    "minecraft:axolotl",
                    "minecraft:bat",
                    "minecraft:camel",
                    "minecraft:cat",
                    "minecraft:chicken",
                    "minecraft:cod",
                    "minecraft:cow",
                    "minecraft:donkey",
                    "minecraft:frog",
                    "minecraft:glow_squid",
                    "minecraft:horse",
                    "minecraft:mooshroom",
                    "minecraft:mule",
                    "minecraft:ocelot",
                    "minecraft:parrot",
                    "minecraft:pig",
                    "minecraft:rabbit",
                    "minecraft:salmon",
                    "minecraft:sheep",
                    "minecraft:skeleton_horse",
                    "minecraft:sniffer",
                    "minecraft:squid",
                    "minecraft:strider",
                    "minecraft:tadpole",
                    "minecraft:tropical_fish",
                    "minecraft:turtle"
            );

    private final Category category;

    private final double recognitionRadius;
    private final double pursuitRadius;
    private final double interactionDistance;

    private final double repathTolerance;
    private final double movingTargetTolerance;

    private final int maxCancelledApproaches;

    private DAI_ApproachProfile(
            Category category,
            double recognitionRadius,
            double pursuitRadius,
            double interactionDistance,
            double repathTolerance,
            double movingTargetTolerance,
            int maxCancelledApproaches
    ) {

        this.category =
                category;

        this.recognitionRadius =
                recognitionRadius;

        this.pursuitRadius =
                pursuitRadius;

        this.interactionDistance =
                interactionDistance;

        this.repathTolerance =
                repathTolerance;

        this.movingTargetTolerance =
                movingTargetTolerance;

        this.maxCancelledApproaches =
                maxCancelledApproaches;
    }

    public static DAI_ApproachProfile forEntity(
            LivingEntity entity
    ) {

        if (entity == null) {
            return neutral();
        }

        String id =
                entityId(
                        entity
                );

        if (
                BOSS_TYPES.contains(
                        id
                )
        ) {
            return boss();
        }

        if (
                VILLAGER_TYPES.contains(
                        id
                )
        ) {
            return villager();
        }

        if (
                FLYING_TYPES.contains(
                        id
                )
        ) {

            if (
                    HOSTILE_TYPES.contains(
                            id
                    )
            ) {
                return hostileFlying();
            }

            return flying();
        }

        if (
                AQUATIC_TYPES.contains(
                        id
                )
        ) {
            return aquatic();
        }

        if (
                RANGED_HOSTILE_TYPES.contains(
                        id
                )
        ) {
            return hostileRanged();
        }

        if (
                HOSTILE_TYPES.contains(
                        id
                )
        ) {
            return hostileMelee();
        }

        if (
                PASSIVE_TYPES.contains(
                        id
                )
        ) {
            return passive();
        }

        return neutral();
    }

    public static DAI_ApproachProfile passive() {

        return new DAI_ApproachProfile(
                Category.PASSIVE,
                24.0D,
                14.0D,
                2.60D,
                1.50D,
                1.25D,
                2
        );
    }

    public static DAI_ApproachProfile neutral() {

        return new DAI_ApproachProfile(
                Category.NEUTRAL,
                20.0D,
                12.0D,
                2.65D,
                1.50D,
                1.25D,
                2
        );
    }

    public static DAI_ApproachProfile hostileMelee() {

        return new DAI_ApproachProfile(
                Category.HOSTILE_MELEE,
                28.0D,
                20.0D,
                2.75D,
                2.00D,
                2.00D,
                4
        );
    }

    public static DAI_ApproachProfile hostileRanged() {

        return new DAI_ApproachProfile(
                Category.HOSTILE_RANGED,
                32.0D,
                22.0D,
                2.75D,
                2.50D,
                2.50D,
                4
        );
    }

    public static DAI_ApproachProfile villager() {

        return new DAI_ApproachProfile(
                Category.VILLAGER,
                20.0D,
                10.0D,
                2.40D,
                1.25D,
                1.00D,
                2
        );
    }

    public static DAI_ApproachProfile flying() {

        return new DAI_ApproachProfile(
                Category.FLYING,
                20.0D,
                8.0D,
                2.75D,
                2.50D,
                3.00D,
                1
        );
    }

    public static DAI_ApproachProfile hostileFlying() {

        return new DAI_ApproachProfile(
                Category.FLYING,
                32.0D,
                12.0D,
                2.75D,
                3.00D,
                4.00D,
                2
        );
    }

    public static DAI_ApproachProfile aquatic() {

        return new DAI_ApproachProfile(
                Category.AQUATIC,
                18.0D,
                8.0D,
                2.60D,
                2.00D,
                2.50D,
                1
        );
    }

    public static DAI_ApproachProfile boss() {

        return new DAI_ApproachProfile(
                Category.BOSS,
                96.0D,
                48.0D,
                3.00D,
                6.00D,
                8.00D,
                8
        );
    }

    private static String entityId(
            Entity entity
    ) {

        Identifier identifier =
                BuiltInRegistries.ENTITY_TYPE
                        .getKey(
                                entity.getType()
                        );

        if (identifier == null) {
            return "";
        }

        return identifier.toString();
    }

    public Category category() {
        return category;
    }

    public double recognitionRadius() {
        return recognitionRadius;
    }

    public double pursuitRadius() {
        return pursuitRadius;
    }

    public double interactionDistance() {
        return interactionDistance;
    }

    public double repathTolerance() {
        return repathTolerance;
    }

    public double movingTargetTolerance() {
        return movingTargetTolerance;
    }

    public int maxCancelledApproaches() {
        return maxCancelledApproaches;
    }
}