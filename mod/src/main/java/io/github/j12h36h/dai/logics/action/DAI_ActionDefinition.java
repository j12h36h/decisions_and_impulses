package io.github.j12h36h.dai.logics.action;

import com.mojang.serialization.Codec;
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
        double value
) {

    public static final Codec<DAI_ActionDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .optionalFieldOf(
                                            "type",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::type
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "action",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::action
                                    ),

                            DAI_ConditionDefinition.CODEC
                                    .listOf()
                                    .optionalFieldOf(
                                            "conditions",
                                            List.of()
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::conditions
                                    ),

                            Codec.lazyInitialized(
                                            () -> DAI_ActionDefinition.CODEC
                                    )
                                    .listOf()
                                    .optionalFieldOf(
                                            "sequence",
                                            List.of()
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::sequence
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "menu",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::menu
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "open",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::open
                                    ),

                            Codec.FLOAT
                                    .optionalFieldOf(
                                            "yaw",
                                            0.0F
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::yaw
                                    ),

                            Codec.FLOAT
                                    .optionalFieldOf(
                                            "pitch",
                                            0.0F
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::pitch
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "direction",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::direction
                                    ),

                            Codec.INT
                                    .optionalFieldOf(
                                            "ticks",
                                            0
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::ticks
                                    ),

                            Codec.INT
                                    .optionalFieldOf(
                                            "slot",
                                            0
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::slot
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "state",
                                            false
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::state
                                    ),
                            Codec.DOUBLE
                                    .optionalFieldOf(
                                            "value",
                                            0.0D
                                    )
                                    .forGetter(
                                            DAI_ActionDefinition::value
                                    )
                    ).apply(
                            instance,
                            DAI_ActionDefinition::new
                    )
            );

    public DAI_ActionDefinition {

        type = normalize(type);
        action = normalize(action);
        menu = normalize(menu);
        open = normalize(open);
        direction = normalize(direction);

        conditions =
                conditions == null
                        ? List.of()
                        : List.copyOf(conditions);

        sequence =
                sequence == null
                        ? List.of()
                        : List.copyOf(sequence);

        if (
                conditions.stream()
                        .anyMatch(condition ->
                                condition == null
                        )
        ) {

            throw new IllegalArgumentException(
                    "Action conditions cannot contain null entries."
            );
        }

        if (
                sequence.stream()
                        .anyMatch(childAction ->
                                childAction == null
                        )
        ) {

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

        if (!Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    "Action value must be finite."
            );
        }
    }

    public DAI_ActionDefinition(
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
            int slot
    ) {

        this(
                type,
                action,
                conditions,
                sequence,
                menu,
                open,
                yaw,
                pitch,
                direction,
                ticks,
                slot,
                false,
                0.0D
        );
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

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }
}