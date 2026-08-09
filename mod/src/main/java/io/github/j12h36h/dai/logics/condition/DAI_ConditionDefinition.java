package io.github.j12h36h.dai.logics.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;

public record DAI_ConditionDefinition(
        String type,
        String operator,
        boolean booleanValue,
        double numberValue,
        String stringValue,
        boolean negate,
        String parameter,
        double parameterNumber,
        List<DAI_ConditionDefinition> conditions
) {

    public static final Codec<DAI_ConditionDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING
                                    .fieldOf("type")
                                    .forGetter(
                                            DAI_ConditionDefinition::type
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "operator",
                                            "is_true"
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::operator
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "boolean_value",
                                            false
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::booleanValue
                                    ),

                            Codec.DOUBLE
                                    .optionalFieldOf(
                                            "number_value",
                                            0.0D
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::numberValue
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "string_value",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::stringValue
                                    ),

                            Codec.BOOL
                                    .optionalFieldOf(
                                            "negate",
                                            false
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::negate
                                    ),

                            Codec.STRING
                                    .optionalFieldOf(
                                            "parameter",
                                            ""
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::parameter
                                    ),

                            Codec.DOUBLE
                                    .optionalFieldOf(
                                            "parameter_number",
                                            0.0D
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::parameterNumber
                                    ),

                            Codec.lazyInitialized(
                                            () -> DAI_ConditionDefinition.CODEC
                                    )
                                    .listOf()
                                    .optionalFieldOf(
                                            "conditions",
                                            List.of()
                                    )
                                    .forGetter(
                                            DAI_ConditionDefinition::conditions
                                    )
                    ).apply(
                            instance,
                            DAI_ConditionDefinition::new
                    )
            );

    public DAI_ConditionDefinition {

        type = normalizeRequired(
                type,
                "Condition type"
        ).toLowerCase(
                Locale.ROOT
        );

        operator = normalizeOptional(
                operator,
                "is_true"
        ).toLowerCase(
                Locale.ROOT
        );

        stringValue = normalizeOptional(
                stringValue,
                ""
        );

        parameter = normalizeOptional(
                parameter,
                ""
        ).toLowerCase(
                Locale.ROOT
        );

        conditions = conditions == null
                ? List.of()
                : List.copyOf(conditions);

        if (
                conditions.stream()
                        .anyMatch(child -> child == null)
        ) {
            throw new IllegalArgumentException(
                    "Condition groups cannot contain null entries."
            );
        }

        if (!Double.isFinite(numberValue)) {
            throw new IllegalArgumentException(
                    "Condition number value must be finite."
            );
        }

        if (!Double.isFinite(parameterNumber)) {
            throw new IllegalArgumentException(
                    "Condition parameter number must be finite."
            );
        }
    }

    public DAI_ConditionDefinition(
            String type
    ) {
        this(
                type,
                "is_true",
                false,
                0.0D,
                "",
                false,
                "",
                0.0D,
                List.of()
        );
    }

    public DAI_ConditionDefinition(
            String type,
            String operator,
            boolean booleanValue,
            double numberValue,
            String stringValue,
            boolean negate
    ) {
        this(
                type,
                operator,
                booleanValue,
                numberValue,
                stringValue,
                negate,
                "",
                0.0D,
                List.of()
        );
    }

    public DAI_ConditionDefinition(
            String type,
            String operator,
            boolean booleanValue,
            double numberValue,
            String stringValue,
            boolean negate,
            String parameter,
            double parameterNumber
    ) {
        this(
                type,
                operator,
                booleanValue,
                numberValue,
                stringValue,
                negate,
                parameter,
                parameterNumber,
                List.of()
        );
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    private static String normalizeRequired(
            String value,
            String name
    ) {

        if (value == null) {
            throw new IllegalArgumentException(
                    name + " cannot be null."
            );
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " cannot be empty."
            );
        }

        return normalized;
    }

    private static String normalizeOptional(
            String value,
            String fallback
    ) {

        if (value == null) {
            return fallback;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? fallback
                : normalized;
    }
}