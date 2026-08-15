package io.github.j12h36h.dai.client.objectives.recognition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DAI_RecogScanDefinition(
        String mode,
        String origin,
        int originRadius,
        int maxRadius,
        int maxBlocks
) {

    public static final String MODE_CONNECTED =
            "connected";

    public static final String MODE_BOUNDED_REGION =
            "bounded_region";

    public static final String MODE_VOLUME =
            "volume";

    public static final String MODE_REGIONAL =
            "regional";


    public static final String ORIGIN_TARGETED_BLOCK =
            "targeted_block";

    public static final String ORIGIN_NEAREST_MATCH =
            "nearest_match";

    public static final String ORIGIN_PLAYER =
            "player";


    public static final DAI_RecogScanDefinition DEFAULT =
            new DAI_RecogScanDefinition(
                    MODE_CONNECTED,
                    ORIGIN_TARGETED_BLOCK,
                    0,
                    12,
                    512
            );


    public static final Codec<DAI_RecogScanDefinition> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.STRING
                                                    .optionalFieldOf(
                                                            "mode",
                                                            MODE_CONNECTED
                                                    )
                                                    .forGetter(
                                                            DAI_RecogScanDefinition::mode
                                                    ),

                                            Codec.STRING
                                                    .optionalFieldOf(
                                                            "origin",
                                                            ORIGIN_TARGETED_BLOCK
                                                    )
                                                    .forGetter(
                                                            DAI_RecogScanDefinition::origin
                                                    ),

                                            Codec.INT
                                                    .optionalFieldOf(
                                                            "origin_radius",
                                                            0
                                                    )
                                                    .forGetter(
                                                            DAI_RecogScanDefinition::originRadius
                                                    ),

                                            Codec.INT
                                                    .optionalFieldOf(
                                                            "max_radius",
                                                            12
                                                    )
                                                    .forGetter(
                                                            DAI_RecogScanDefinition::maxRadius
                                                    ),

                                            Codec.INT
                                                    .optionalFieldOf(
                                                            "max_blocks",
                                                            512
                                                    )
                                                    .forGetter(
                                                            DAI_RecogScanDefinition::maxBlocks
                                                    )
                                    )
                                    .apply(
                                            instance,
                                            DAI_RecogScanDefinition::new
                                    )
            );


    public DAI_RecogScanDefinition {

        mode =
                normalize(
                        mode,
                        MODE_CONNECTED
                );

        origin =
                normalize(
                        origin,
                        ORIGIN_TARGETED_BLOCK
                );

        if (originRadius < 0) {

            throw new IllegalArgumentException(
                    "Recognition scan origin radius cannot be negative."
            );
        }

        if (maxRadius < 1) {

            throw new IllegalArgumentException(
                    "Recognition scan maximum radius must be at least 1."
            );
        }

        if (maxBlocks < 1) {

            throw new IllegalArgumentException(
                    "Recognition scan maximum block count must be at least 1."
            );
        }
    }


    public boolean connected() {

        return MODE_CONNECTED.equals(
                mode
        );
    }


    public boolean boundedRegion() {

        return MODE_BOUNDED_REGION.equals(
                mode
        );
    }


    public boolean volume() {

        return MODE_VOLUME.equals(
                mode
        );
    }


    public boolean regional() {

        return MODE_REGIONAL.equals(
                mode
        );
    }


    public boolean targetedBlockOrigin() {

        return ORIGIN_TARGETED_BLOCK.equals(
                origin
        );
    }


    public boolean nearestMatchOrigin() {

        return ORIGIN_NEAREST_MATCH.equals(
                origin
        );
    }


    public boolean playerOrigin() {

        return ORIGIN_PLAYER.equals(
                origin
        );
    }


    private static String normalize(
            String value,
            String fallback
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {
            return fallback;
        }

        return value.trim()
                .toLowerCase();
    }
}