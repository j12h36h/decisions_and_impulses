package io.github.j12h36h.dai.objectives.recognition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.stream.Collectors;

public record DAI_RecogDefinition(
        String type,
        DAI_RecogScan scan,
        Map<String, DAI_RecogGroupRule> groups,
        List<DAI_RecogRequirement> requirements,
        DAI_RecogResultDefinition result
) {

    public static final Codec<DAI_RecogDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                                    Codec.STRING
                                            .optionalFieldOf(
                                                    "type",
                                                    "structure"
                                            )
                                            .forGetter(
                                                    DAI_RecogDefinition::type
                                            ),

                                    DAI_RecogScan.CODEC
                                            .fieldOf(
                                                    "scan"
                                            )
                                            .forGetter(
                                                    DAI_RecogDefinition::scan
                                            ),

                                    Codec.unboundedMap(
                                                    Codec.STRING,
                                                    DAI_RecogGroupRule.CODEC
                                            )
                                            .fieldOf(
                                                    "groups"
                                            )
                                            .forGetter(
                                                    DAI_RecogDefinition::groups
                                            ),

                                    DAI_RecogRequirement.CODEC
                                            .listOf()
                                            .optionalFieldOf(
                                                    "requirements",
                                                    List.of()
                                            )
                                            .forGetter(
                                                    DAI_RecogDefinition::requirements
                                            ),

                                    DAI_RecogResultDefinition.CODEC
                                            .fieldOf(
                                                    "result"
                                            )
                                            .forGetter(
                                                    DAI_RecogDefinition::result
                                            )
                            )
                            .apply(
                                    instance,
                                    DAI_RecogDefinition::new
                            )
            );

    public DAI_RecogDefinition {

        type =
                normalize(
                        type
                );

        if (type.isEmpty()) {

            throw new IllegalArgumentException(
                    "Recognition definition type cannot be blank."
            );
        }

        if (scan == null) {

            throw new IllegalArgumentException(
                    "Recognition scan definition cannot be null."
            );
        }

        if (
                groups == null
                        || groups.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Recognition definition must contain at least one group."
            );
        }

        groups =
                Map.copyOf(
                        groups
                );

        requirements =
                requirements == null
                        ? List.of()
                        : List.copyOf(
                        requirements
                );

        if (result == null) {

            throw new IllegalArgumentException(
                    "Recognition result definition cannot be null."
            );
        }
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

    public record DAI_RecogScan(
            String mode,
            String origin,
            int maxBlocks,
            int maxRadius,
            int horizontalRadius,
            int upwardRange,
            int downwardRange
    ) {

        public static final Codec<DAI_RecogScan> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "mode",
                                                        "connected"
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::mode
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "origin",
                                                        "targeted_block"
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::origin
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "max_blocks",
                                                        512
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::maxBlocks
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "max_radius",
                                                        12
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::maxRadius
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "horizontal_radius",
                                                        12
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::horizontalRadius
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "upward_range",
                                                        12
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::upwardRange
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "downward_range",
                                                        4
                                                )
                                                .forGetter(
                                                        DAI_RecogScan::downwardRange
                                                )
                                )
                                .apply(
                                        instance,
                                        DAI_RecogScan::new
                                )
                );

        public DAI_RecogScan {

            mode =
                    normalize(
                            mode
                    );

            origin =
                    normalize(
                            origin
                    );

            if (mode.isEmpty()) {

                throw new IllegalArgumentException(
                        "Recognition scan mode cannot be blank."
                );
            }

            if (origin.isEmpty()) {

                throw new IllegalArgumentException(
                        "Recognition scan origin cannot be blank."
                );
            }

            if (maxBlocks <= 0) {

                throw new IllegalArgumentException(
                        "Recognition max_blocks must be greater than zero."
                );
            }

            if (maxRadius <= 0) {

                throw new IllegalArgumentException(
                        "Recognition max_radius must be greater than zero."
                );
            }

            if (horizontalRadius <= 0) {

                throw new IllegalArgumentException(
                        "Recognition horizontal_radius must be greater than zero."
                );
            }

            if (upwardRange < 0) {

                throw new IllegalArgumentException(
                        "Recognition upward_range cannot be negative."
                );
            }

            if (downwardRange < 0) {

                throw new IllegalArgumentException(
                        "Recognition downward_range cannot be negative."
                );
            }
        }
    }

    public record DAI_RecogGroupRule(
            String registry,
            int minimum,
            int maximum
    ) {

        public static final Codec<DAI_RecogGroupRule> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                        Codec.STRING
                                                .fieldOf(
                                                        "registry"
                                                )
                                                .forGetter(
                                                        DAI_RecogGroupRule::registry
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "minimum",
                                                        0
                                                )
                                                .forGetter(
                                                        DAI_RecogGroupRule::minimum
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "maximum",
                                                        Integer.MAX_VALUE
                                                )
                                                .forGetter(
                                                        DAI_RecogGroupRule::maximum
                                                )
                                )
                                .apply(
                                        instance,
                                        DAI_RecogGroupRule::new
                                )
                );

        public DAI_RecogGroupRule {

            registry =
                    normalize(
                            registry
                    );

            if (registry.isEmpty()) {

                throw new IllegalArgumentException(
                        "Recognition group registry cannot be blank."
                );
            }

            if (minimum < 0) {

                throw new IllegalArgumentException(
                        "Recognition group minimum cannot be negative."
                );
            }

            if (maximum < minimum) {

                throw new IllegalArgumentException(
                        "Recognition group maximum cannot be less than its minimum."
                );
            }
        }
    }

    public record DAI_RecogRequirement(
            String type,
            String group,
            String relativeTo,
            List<String> groups,
            int minimumHeight,
            float minimumRatio,
            Map<String, JsonElement> parameters
    ) {

        private static final Codec<JsonElement> JSON_ELEMENT_CODEC =
                Codec.PASSTHROUGH.xmap(
                        dynamic ->
                                dynamic.convert(
                                        JsonOps.INSTANCE
                                ).getValue(),

                        json ->
                                new Dynamic<>(
                                        JsonOps.INSTANCE,
                                        json
                                )
                );

        public static final Codec<DAI_RecogRequirement> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                        Codec.STRING
                                                .fieldOf(
                                                        "type"
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::type
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "group",
                                                        ""
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::group
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "relative_to",
                                                        ""
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::relativeTo
                                                ),

                                        Codec.STRING
                                                .listOf()
                                                .optionalFieldOf(
                                                        "groups",
                                                        List.of()
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::groups
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "minimum_height",
                                                        0
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::minimumHeight
                                                ),

                                        Codec.FLOAT
                                                .optionalFieldOf(
                                                        "minimum_ratio",
                                                        0.0F
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::minimumRatio
                                                ),

                                        Codec.unboundedMap(
                                                        Codec.STRING,
                                                        JSON_ELEMENT_CODEC
                                                )
                                                .optionalFieldOf(
                                                        "parameters",
                                                        Map.of()
                                                )
                                                .forGetter(
                                                        DAI_RecogRequirement::parameters
                                                )
                                )
                                .apply(
                                        instance,
                                        DAI_RecogRequirement::new
                                )
                );

        public DAI_RecogRequirement {

            type =
                    normalize(
                            type
                    );

            group =
                    normalize(
                            group
                    );

            relativeTo =
                    normalize(
                            relativeTo
                    );

            groups =
                    groups == null
                            ? List.of()
                            : groups.stream()
                            .map(
                                    DAI_RecogDefinition::normalize
                            )
                            .filter(entry ->
                                    !entry.isEmpty()
                            )
                            .distinct()
                            .toList();

            parameters =
                    normalizeParameters(
                            parameters
                    );

            if (type.isEmpty()) {

                throw new IllegalArgumentException(
                        "Recognition requirement type cannot be blank."
                );
            }

            if (minimumHeight < 0) {

                throw new IllegalArgumentException(
                        "Recognition minimum height cannot be negative."
                );
            }

            if (minimumRatio < 0.0F) {

                throw new IllegalArgumentException(
                        "Recognition minimum ratio cannot be negative."
                );
            }
        }

        public boolean hasParameter(
                String name
        ) {

            String normalizedName =
                    normalize(
                            name
                    );

            return !normalizedName.isEmpty()
                    && parameters.containsKey(
                    normalizedName
            );
        }

        public String stringParameter(
                String name,
                String fallback
        ) {

            JsonElement value =
                    parameter(
                            name
                    );

            if (
                    value == null
                            || value.isJsonNull()
                            || !value.isJsonPrimitive()
            ) {
                return fallback;
            }

            JsonPrimitive primitive =
                    value.getAsJsonPrimitive();

            if (!primitive.isString()) {
                return fallback;
            }

            String stringValue =
                    normalize(
                            primitive.getAsString()
                    );

            return stringValue.isEmpty()
                    ? fallback
                    : stringValue;
        }

        public int intParameter(
                String name,
                int fallback
        ) {

            JsonElement value =
                    parameter(
                            name
                    );

            if (
                    value == null
                            || value.isJsonNull()
                            || !value.isJsonPrimitive()
            ) {
                return fallback;
            }

            JsonPrimitive primitive =
                    value.getAsJsonPrimitive();

            if (!primitive.isNumber()) {
                return fallback;
            }

            try {

                return primitive.getAsInt();

            } catch (
                    NumberFormatException
                    | UnsupportedOperationException exception
            ) {

                return fallback;
            }
        }

        public float floatParameter(
                String name,
                float fallback
        ) {

            JsonElement value =
                    parameter(
                            name
                    );

            if (
                    value == null
                            || value.isJsonNull()
                            || !value.isJsonPrimitive()
            ) {
                return fallback;
            }

            JsonPrimitive primitive =
                    value.getAsJsonPrimitive();

            if (!primitive.isNumber()) {
                return fallback;
            }

            try {

                return primitive.getAsFloat();

            } catch (
                    NumberFormatException
                    | UnsupportedOperationException exception
            ) {

                return fallback;
            }
        }

        public boolean booleanParameter(
                String name,
                boolean fallback
        ) {

            JsonElement value =
                    parameter(
                            name
                    );

            if (
                    value == null
                            || value.isJsonNull()
                            || !value.isJsonPrimitive()
            ) {
                return fallback;
            }

            JsonPrimitive primitive =
                    value.getAsJsonPrimitive();

            if (!primitive.isBoolean()) {
                return fallback;
            }

            return primitive.getAsBoolean();
        }

        private JsonElement parameter(
                String name
        ) {

            String normalizedName =
                    normalize(
                            name
                    );

            if (normalizedName.isEmpty()) {
                return null;
            }

            return parameters.get(
                    normalizedName
            );
        }

        private static Map<String, JsonElement> normalizeParameters(
                Map<String, JsonElement> parameters
        ) {

            if (
                    parameters == null
                            || parameters.isEmpty()
            ) {
                return Map.of();
            }

            return parameters.entrySet()
                    .stream()
                    .filter(entry ->
                            entry.getKey() != null
                                    && !entry.getKey().isBlank()
                                    && entry.getValue() != null
                    )
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    entry ->
                                            normalize(
                                                    entry.getKey()
                                            ),
                                    Map.Entry::getValue,
                                    (first, replacement) ->
                                            replacement
                            )
                    );
        }
    }

    public record DAI_RecogResultDefinition(
            String id
    ) {

        public static final Codec<DAI_RecogResultDefinition> CODEC =
                RecordCodecBuilder.create(instance ->
                        instance.group(
                                        Codec.STRING
                                                .fieldOf(
                                                        "id"
                                                )
                                                .forGetter(
                                                        DAI_RecogResultDefinition::id
                                                )
                                )
                                .apply(
                                        instance,
                                        DAI_RecogResultDefinition::new
                                )
                );

        public DAI_RecogResultDefinition {

            id =
                    normalize(
                            id
                    );

            if (id.isEmpty()) {

                throw new IllegalArgumentException(
                        "Recognition result id cannot be blank."
                );
            }
        }
    }
}
