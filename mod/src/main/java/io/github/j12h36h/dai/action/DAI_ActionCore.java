package io.github.j12h36h.dai.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.j12h36h.dai.condition.DAI_Condition;

import java.util.List;

public record DAI_ActionCore(
        String type,
        String action,
        List<DAI_Condition> conditions,
        List<DAI_ActionCore> sequence,
        String menu,
        String open,
        float yaw,
        float pitch,
        String direction,
        int ticks
) {

    public static final Codec<DAI_ActionCore> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .optionalFieldOf("type", "")
                                    .forGetter(DAI_ActionCore::type),

                            Codec.STRING
                                    .optionalFieldOf("action", "")
                                    .forGetter(DAI_ActionCore::action),

                            DAI_Condition.CODEC
                                    .listOf()
                                    .optionalFieldOf(
                                            "conditions",
                                            List.of()
                                    )
                                    .forGetter(DAI_ActionCore::conditions),

                            Codec.lazyInitialized(
                                            () -> DAI_ActionCore.CODEC
                                    )
                                    .listOf()
                                    .optionalFieldOf(
                                            "sequence",
                                            List.of()
                                    )
                                    .forGetter(DAI_ActionCore::sequence),

                            Codec.STRING
                                    .optionalFieldOf("menu", "")
                                    .forGetter(DAI_ActionCore::menu),

                            Codec.STRING
                                    .optionalFieldOf("open", "")
                                    .forGetter(DAI_ActionCore::open),

                            Codec.FLOAT
                                    .optionalFieldOf("yaw", 0.0F)
                                    .forGetter(DAI_ActionCore::yaw),

                            Codec.FLOAT
                                    .optionalFieldOf("pitch", 0.0F)
                                    .forGetter(DAI_ActionCore::pitch),

                            Codec.STRING
                                    .optionalFieldOf("direction", "")
                                    .forGetter(DAI_ActionCore::direction),

                            Codec.INT
                                    .optionalFieldOf("ticks", 0)
                                    .forGetter(DAI_ActionCore::ticks)
                    ).apply(
                            instance,
                            DAI_ActionCore::new
                    )
            );

    public DAI_ActionCore {

        type = normalize(type);
        action = normalize(action);
        menu = normalize(menu);
        open = normalize(open);
        direction = normalize(direction);

        conditions = conditions == null
                ? List.of()
                : List.copyOf(conditions);

        sequence = sequence == null
                ? List.of()
                : List.copyOf(sequence);

        if (conditions.stream().anyMatch(condition -> condition == null)) {
            throw new IllegalArgumentException(
                    "Action conditions cannot contain null entries."
            );
        }

        if (sequence.stream().anyMatch(childAction -> childAction == null)) {
            throw new IllegalArgumentException(
                    "Action sequence cannot contain null entries."
            );
        }

        if (!Float.isFinite(yaw)) {
            throw new IllegalArgumentException(
                    "Action yaw must be finite."
            );
        }

        if (!Float.isFinite(pitch)) {
            throw new IllegalArgumentException(
                    "Action pitch must be finite."
            );
        }

        if (ticks < 0) {
            throw new IllegalArgumentException(
                    "Action ticks cannot be negative."
            );
        }
    }

    public boolean hasType() {
        return !type.isEmpty();
    }

    public boolean hasAction() {
        return !action.isEmpty();
    }

    public boolean hasSequence() {
        return !sequence.isEmpty();
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim();
    }
}