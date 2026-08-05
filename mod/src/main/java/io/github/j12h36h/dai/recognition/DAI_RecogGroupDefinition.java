package io.github.j12h36h.dai.recognition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Locale;

public record DAI_RecogGroupDefinition(
        boolean replace,
        List<String> entries
) {

    public static final Codec<DAI_RecogGroupDefinition> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.BOOL
                                    .optionalFieldOf(
                                            "replace",
                                            false
                                    )
                                    .forGetter(
                                            DAI_RecogGroupDefinition::replace
                                    ),
                            Codec.STRING
                                    .listOf()
                                    .fieldOf("entries")
                                    .forGetter(
                                            DAI_RecogGroupDefinition::entries
                                    )
                    ).apply(
                            instance,
                            DAI_RecogGroupDefinition::new
                    )
            );

    public DAI_RecogGroupDefinition {

        if (entries == null) {

            throw new IllegalArgumentException(
                    "Recognition group entries cannot be null."
            );
        }

        entries =
                entries.stream()
                        .map(
                                DAI_RecogGroupDefinition::normalize
                        )
                        .filter(entry ->
                                !entry.isEmpty()
                        )
                        .distinct()
                        .toList();

        if (entries.isEmpty()) {

            throw new IllegalArgumentException(
                    "Recognition group must contain at least one entry."
            );
        }
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}
