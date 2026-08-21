package io.github.j12h36h.dai.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;

/**
 * Static native-block properties owned by a registry-backed DAI block.
 *
 * Values in this record are consumed while Minecraft's static BLOCK registry
 * is being constructed. Runtime callbacks remain in DAI_ContentDefinition's
 * normal events map so event edits stay hot-reloadable.
 */
public record DAI_BlockSettings(
        float hardness,
        float explosionResistance,
        String sound,
        int luminance,
        float friction,
        float speedFactor,
        float jumpFactor,
        boolean requiresCorrectTool,
        boolean noOcclusion,
        boolean noCollision,
        boolean replaceable,
        boolean randomTicks,
        boolean ignitedByLava,
        boolean emissiveRendering,
        String mapColor,
        String pushReaction,
        List<String> states,
        List<Double> outlineShape,
        List<Double> collisionShape,
        int redstoneSignal,
        boolean climbable,
        String redstoneState,
        String useToggleState,
        int scheduledTickDelay
) {

    public static final DAI_BlockSettings DEFAULT = new DAI_BlockSettings(
            1.5F, 6.0F, "stone", 0,
            0.6F, 1.0F, 1.0F,
            false, false, false, false, false, false, false,
            "", "normal",
            List.of(), List.of(), List.of(), 0, false, "", "", 0
    );

    /**
     * DFU's RecordCodecBuilder.group(...) has a finite arity. Keep the public
     * JSON flat by composing two MapCodec fragments into the same record map.
     */
    private record PhysicalPart(
            float hardness,
            float explosionResistance,
            String sound,
            int luminance,
            float friction,
            float speedFactor,
            float jumpFactor,
            boolean requiresCorrectTool,
            boolean noOcclusion,
            boolean noCollision,
            boolean replaceable,
            boolean randomTicks,
            boolean ignitedByLava,
            boolean emissiveRendering
    ) {}

    private record BehaviorPart(
            String mapColor,
            String pushReaction,
            List<String> states,
            List<Double> outlineShape,
            List<Double> collisionShape,
            int redstoneSignal,
            boolean climbable,
            String redstoneState,
            String useToggleState,
            int scheduledTickDelay
    ) {}

    private static final MapCodec<PhysicalPart> PHYSICAL_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.FLOAT.optionalFieldOf("hardness", 1.5F).forGetter(PhysicalPart::hardness),
                    Codec.FLOAT.optionalFieldOf("explosion_resistance", 6.0F).forGetter(PhysicalPart::explosionResistance),
                    Codec.STRING.optionalFieldOf("sound", "stone").forGetter(PhysicalPart::sound),
                    Codec.INT.optionalFieldOf("luminance", 0).forGetter(PhysicalPart::luminance),
                    Codec.FLOAT.optionalFieldOf("friction", 0.6F).forGetter(PhysicalPart::friction),
                    Codec.FLOAT.optionalFieldOf("speed_factor", 1.0F).forGetter(PhysicalPart::speedFactor),
                    Codec.FLOAT.optionalFieldOf("jump_factor", 1.0F).forGetter(PhysicalPart::jumpFactor),
                    Codec.BOOL.optionalFieldOf("requires_correct_tool", false).forGetter(PhysicalPart::requiresCorrectTool),
                    Codec.BOOL.optionalFieldOf("no_occlusion", false).forGetter(PhysicalPart::noOcclusion),
                    Codec.BOOL.optionalFieldOf("no_collision", false).forGetter(PhysicalPart::noCollision),
                    Codec.BOOL.optionalFieldOf("replaceable", false).forGetter(PhysicalPart::replaceable),
                    Codec.BOOL.optionalFieldOf("random_ticks", false).forGetter(PhysicalPart::randomTicks),
                    Codec.BOOL.optionalFieldOf("ignited_by_lava", false).forGetter(PhysicalPart::ignitedByLava),
                    Codec.BOOL.optionalFieldOf("emissive_rendering", false).forGetter(PhysicalPart::emissiveRendering)
            ).apply(instance, PhysicalPart::new));

    private static final MapCodec<BehaviorPart> BEHAVIOR_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("map_color", "").forGetter(BehaviorPart::mapColor),
                    Codec.STRING.optionalFieldOf("push_reaction", "normal").forGetter(BehaviorPart::pushReaction),
                    Codec.STRING.listOf().optionalFieldOf("states", List.of()).forGetter(BehaviorPart::states),
                    Codec.DOUBLE.listOf().optionalFieldOf("outline_shape", List.of()).forGetter(BehaviorPart::outlineShape),
                    Codec.DOUBLE.listOf().optionalFieldOf("collision_shape", List.of()).forGetter(BehaviorPart::collisionShape),
                    Codec.INT.optionalFieldOf("redstone_signal", 0).forGetter(BehaviorPart::redstoneSignal),
                    Codec.BOOL.optionalFieldOf("climbable", false).forGetter(BehaviorPart::climbable),
                    Codec.STRING.optionalFieldOf("redstone_state", "").forGetter(BehaviorPart::redstoneState),
                    Codec.STRING.optionalFieldOf("use_toggle_state", "").forGetter(BehaviorPart::useToggleState),
                    Codec.INT.optionalFieldOf("scheduled_tick_delay", 0).forGetter(BehaviorPart::scheduledTickDelay)
            ).apply(instance, BehaviorPart::new));

    public static final Codec<DAI_BlockSettings> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.of(DAI_BlockSettings::physicalPart, PHYSICAL_CODEC),
                    RecordCodecBuilder.of(DAI_BlockSettings::behaviorPart, BEHAVIOR_CODEC)
            ).apply(instance, DAI_BlockSettings::fromParts));

    private PhysicalPart physicalPart() {
        return new PhysicalPart(
                hardness, explosionResistance, sound, luminance,
                friction, speedFactor, jumpFactor,
                requiresCorrectTool, noOcclusion, noCollision, replaceable,
                randomTicks, ignitedByLava, emissiveRendering
        );
    }

    private BehaviorPart behaviorPart() {
        return new BehaviorPart(
                mapColor, pushReaction, states, outlineShape, collisionShape,
                redstoneSignal, climbable, redstoneState, useToggleState, scheduledTickDelay
        );
    }

    private static DAI_BlockSettings fromParts(PhysicalPart physical, BehaviorPart behavior) {
        return new DAI_BlockSettings(
                physical.hardness(), physical.explosionResistance(), physical.sound(), physical.luminance(),
                physical.friction(), physical.speedFactor(), physical.jumpFactor(),
                physical.requiresCorrectTool(), physical.noOcclusion(), physical.noCollision(), physical.replaceable(),
                physical.randomTicks(), physical.ignitedByLava(), physical.emissiveRendering(),
                behavior.mapColor(), behavior.pushReaction(), behavior.states(), behavior.outlineShape(),
                behavior.collisionShape(), behavior.redstoneSignal(), behavior.climbable(), behavior.redstoneState(),
                behavior.useToggleState(), behavior.scheduledTickDelay()
        );
    }

    public DAI_BlockSettings {
        hardness = finiteClamp(hardness, -1.0F, 3_600_000.0F, 1.5F);
        explosionResistance = finiteClamp(explosionResistance, 0.0F, 3_600_000.0F, 6.0F);
        sound = normalize(sound, "stone");
        luminance = Math.max(0, Math.min(15, luminance));
        friction = finiteClamp(friction, 0.0F, 10.0F, 0.6F);
        speedFactor = finiteClamp(speedFactor, 0.0F, 10.0F, 1.0F);
        jumpFactor = finiteClamp(jumpFactor, 0.0F, 10.0F, 1.0F);
        mapColor = normalize(mapColor, "");
        pushReaction = normalize(pushReaction, "normal");
        states = states == null ? List.of() : states.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
        outlineShape = normalizeShape(outlineShape);
        collisionShape = normalizeShape(collisionShape);
        redstoneSignal = Math.max(0, Math.min(15, redstoneSignal));
        redstoneState = normalize(redstoneState, "");
        useToggleState = normalize(useToggleState, "");
        scheduledTickDelay = Math.max(0, Math.min(72000, scheduledTickDelay));
    }

    public boolean hasOutlineShape() { return outlineShape.size() == 6; }
    public boolean hasCollisionShape() { return collisionShape.size() == 6; }

    private static List<Double> normalizeShape(List<Double> values) {
        if (values == null || values.size() != 6) return List.of();
        java.util.ArrayList<Double> out = new java.util.ArrayList<>(6);
        for (Double raw : values) {
            double value = raw == null || !Double.isFinite(raw) ? 0.0D : raw;
            out.add(Math.max(0.0D, Math.min(16.0D, value)));
        }
        return List.copyOf(out);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static float finiteClamp(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
