package io.github.j12h36h.dai.logics.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.logics.condition.DAI_ConditionDefinition;

import java.util.List;

public record DAI_ActionDefinition(
        String type,
        String action,
        List<DAI_ConditionDefinition> conditions,
        List<DAI_ActionDefinition> sequence,
        String menu,
        String open,
        float yaw,
        float pitch,
        String direction,
        int ticks,
        int slot,
        boolean state,
        double value,
        String target,
        DAI_SpriteOverlayDefinition sprite,
        DAI_SpriteSheetOverlayDefinition spriteSheet,
        DAI_ActionArguments arguments
) {

    /* Keep the public JSON map flat while staying below RecordCodecBuilder's arity limit. */
    private record CorePart(
            String type,
            String action,
            List<DAI_ConditionDefinition> conditions,
            List<DAI_ActionDefinition> sequence,
            String menu,
            String open,
            float yaw,
            float pitch,
            String direction
    ) {}

    private record PayloadPart(
            int ticks,
            int slot,
            boolean state,
            double value,
            String target,
            DAI_SpriteOverlayDefinition sprite,
            DAI_SpriteSheetOverlayDefinition spriteSheet,
            DAI_ActionArguments arguments
    ) {}

    private static final MapCodec<CorePart> CORE_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("type", "").forGetter(CorePart::type),
                    Codec.STRING.optionalFieldOf("action", "").forGetter(CorePart::action),
                    DAI_ConditionDefinition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(CorePart::conditions),
                    Codec.lazyInitialized(() -> DAI_ActionDefinition.CODEC).listOf().optionalFieldOf("sequence", List.of()).forGetter(CorePart::sequence),
                    Codec.STRING.optionalFieldOf("menu", "").forGetter(CorePart::menu),
                    Codec.STRING.optionalFieldOf("open", "").forGetter(CorePart::open),
                    Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(CorePart::yaw),
                    Codec.FLOAT.optionalFieldOf("pitch", 0.0F).forGetter(CorePart::pitch),
                    Codec.STRING.optionalFieldOf("direction", "").forGetter(CorePart::direction)
            ).apply(instance, CorePart::new));

    private static final MapCodec<PayloadPart> PAYLOAD_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.optionalFieldOf("ticks", 0).forGetter(PayloadPart::ticks),
                    Codec.INT.optionalFieldOf("slot", 0).forGetter(PayloadPart::slot),
                    Codec.BOOL.optionalFieldOf("state", false).forGetter(PayloadPart::state),
                    Codec.DOUBLE.optionalFieldOf("value", 0.0D).forGetter(PayloadPart::value),
                    Codec.STRING.optionalFieldOf("target", "").forGetter(PayloadPart::target),
                    DAI_SpriteOverlayDefinition.CODEC.optionalFieldOf("sprite", DAI_SpriteOverlayDefinition.EMPTY).forGetter(PayloadPart::sprite),
                    DAI_SpriteSheetOverlayDefinition.CODEC.optionalFieldOf("sprite_sheet", DAI_SpriteSheetOverlayDefinition.EMPTY).forGetter(PayloadPart::spriteSheet),
                    DAI_ActionArguments.CODEC.optionalFieldOf("arguments", DAI_ActionArguments.EMPTY).forGetter(PayloadPart::arguments)
            ).apply(instance, PayloadPart::new));

    public static final Codec<DAI_ActionDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.of(DAI_ActionDefinition::corePart, CORE_CODEC),
                    RecordCodecBuilder.of(DAI_ActionDefinition::payloadPart, PAYLOAD_CODEC)
            ).apply(instance, DAI_ActionDefinition::fromParts));

    private CorePart corePart() {
        return new CorePart(type, action, conditions, sequence, menu, open, yaw, pitch, direction);
    }

    private PayloadPart payloadPart() {
        return new PayloadPart(ticks, slot, state, value, target, sprite, spriteSheet, arguments);
    }

    private static DAI_ActionDefinition fromParts(CorePart core, PayloadPart payload) {
        return new DAI_ActionDefinition(
                core.type(), core.action(), core.conditions(), core.sequence(), core.menu(), core.open(),
                core.yaw(), core.pitch(), core.direction(), payload.ticks(), payload.slot(), payload.state(),
                payload.value(), payload.target(), payload.sprite(), payload.spriteSheet(), payload.arguments()
        );
    }

    public DAI_ActionDefinition {
        type = normalize(type);
        action = normalize(action);
        menu = normalize(menu);
        open = normalize(open);
        direction = normalize(direction);
        target = normalize(target);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        sequence = sequence == null ? List.of() : List.copyOf(sequence);

        if (conditions.stream().anyMatch(condition -> condition == null)) {
            throw new IllegalArgumentException("Action conditions cannot contain null entries.");
        }
        if (sequence.stream().anyMatch(childAction -> childAction == null)) {
            throw new IllegalArgumentException("Action sequence cannot contain null entries.");
        }
        if (!Float.isFinite(yaw)) throw new IllegalArgumentException("Action yaw must be finite.");
        if (!Float.isFinite(pitch)) throw new IllegalArgumentException("Action pitch must be finite.");
        if (ticks < 0) throw new IllegalArgumentException("Action ticks cannot be negative.");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Action value must be finite.");

        sprite = sprite == null ? DAI_SpriteOverlayDefinition.EMPTY : sprite;
        spriteSheet = spriteSheet == null ? DAI_SpriteSheetOverlayDefinition.EMPTY : spriteSheet;
        arguments = arguments == null ? DAI_ActionArguments.EMPTY : arguments;
    }

    public DAI_ActionDefinition(
            String type, String action, List<DAI_ConditionDefinition> conditions, List<DAI_ActionDefinition> sequence,
            String menu, String open, float yaw, float pitch, String direction, int ticks, int slot
    ) {
        this(type, action, conditions, sequence, menu, open, yaw, pitch, direction, ticks, slot,
                false, 0.0D, "", DAI_SpriteOverlayDefinition.EMPTY, DAI_SpriteSheetOverlayDefinition.EMPTY,
                DAI_ActionArguments.EMPTY);
    }

    public DAI_ActionDefinition(
            String type, String action, List<DAI_ConditionDefinition> conditions, List<DAI_ActionDefinition> sequence,
            String menu, String open, float yaw, float pitch, String direction, int ticks, int slot,
            boolean state, double value
    ) {
        this(type, action, conditions, sequence, menu, open, yaw, pitch, direction, ticks, slot, state, value,
                "", DAI_SpriteOverlayDefinition.EMPTY, DAI_SpriteSheetOverlayDefinition.EMPTY, DAI_ActionArguments.EMPTY);
    }

    /** Compatibility overload for the pre-arguments canonical action schema. */
    public DAI_ActionDefinition(
            String type, String action, List<DAI_ConditionDefinition> conditions, List<DAI_ActionDefinition> sequence,
            String menu, String open, float yaw, float pitch, String direction, int ticks, int slot,
            boolean state, double value, String target,
            DAI_SpriteOverlayDefinition sprite, DAI_SpriteSheetOverlayDefinition spriteSheet
    ) {
        this(type, action, conditions, sequence, menu, open, yaw, pitch, direction, ticks, slot, state, value,
                target, sprite, spriteSheet, DAI_ActionArguments.EMPTY);
    }

    /** Compatibility overload for callers compiled before target was added. */
    public DAI_ActionDefinition(
            String type, String action, List<DAI_ConditionDefinition> conditions, List<DAI_ActionDefinition> sequence,
            String menu, String open, float yaw, float pitch, String direction, int ticks, int slot,
            boolean state, double value,
            DAI_SpriteOverlayDefinition sprite, DAI_SpriteSheetOverlayDefinition spriteSheet
    ) {
        this(type, action, conditions, sequence, menu, open, yaw, pitch, direction, ticks, slot, state, value,
                "", sprite, spriteSheet, DAI_ActionArguments.EMPTY);
    }

    public boolean hasType() { return !type.isEmpty(); }
    public boolean hasAction() { return !action.isEmpty(); }
    public boolean hasSequence() { return !sequence.isEmpty(); }
    public boolean hasConditions() { return !conditions.isEmpty(); }
    public boolean hasArguments() { return !arguments.isEmpty(); }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
