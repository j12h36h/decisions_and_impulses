package io.github.j12h36h.dai.client.menus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record DAI_ScreenProfile(
        List<Variant> variants
) {

    public static final Codec<DAI_ScreenProfile> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                    Variant.CODEC
                                            .listOf()
                                            .optionalFieldOf(
                                                    "variants",
                                                    List.of()
                                            )
                                            .forGetter(
                                                    DAI_ScreenProfile::variants
                                            )
                            ).apply(
                                    instance,
                                    DAI_ScreenProfile::new
                            )
            );

    public DAI_ScreenProfile {

        variants =
                variants == null
                        ? List.of()
                        : List.copyOf(
                        variants
                );
    }

    /*
     * ------------------------------------------------------------
     * VARIANT
     * ------------------------------------------------------------
     */

    public record Variant(
            Match match,
            Map<String, Integer> slots,
            Map<String, Control> controls
    ) {

        public static final Codec<Variant> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                        Match.CODEC
                                                .optionalFieldOf(
                                                        "match",
                                                        Match.empty()
                                                )
                                                .forGetter(
                                                        Variant::match
                                                ),

                                        Codec.unboundedMap(
                                                        Codec.STRING,
                                                        Codec.INT
                                                )
                                                .optionalFieldOf(
                                                        "slots",
                                                        Map.of()
                                                )
                                                .forGetter(
                                                        Variant::slots
                                                ),

                                        Codec.unboundedMap(
                                                        Codec.STRING,
                                                        Control.CODEC
                                                )
                                                .optionalFieldOf(
                                                        "controls",
                                                        Map.of()
                                                )
                                                .forGetter(
                                                        Variant::controls
                                                )
                                ).apply(
                                        instance,
                                        Variant::new
                                )
                );

        public Variant {

            match =
                    match == null
                            ? Match.empty()
                            : match;

            slots =
                    slots == null
                            ? Map.of()
                            : Map.copyOf(
                            slots
                    );

            controls =
                    controls == null
                            ? Map.of()
                            : Map.copyOf(
                            controls
                    );
        }

        public Integer slot(
                String name
        ) {

            if (
                    name == null
                            || name.isBlank()
            ) {
                return null;
            }

            return slots.get(
                    normalizeKey(
                            name
                    )
            );
        }

        public Control control(
                String name
        ) {

            if (
                    name == null
                            || name.isBlank()
            ) {
                return null;
            }

            return controls.get(
                    normalizeKey(
                            name
                    )
            );
        }

        public boolean hasSlot(
                String name
        ) {

            return slot(
                    name
            ) != null;
        }

        public boolean hasControl(
                String name
        ) {

            return control(
                    name
            ) != null;
        }
    }

    /*
     * ------------------------------------------------------------
     * MATCH
     * ------------------------------------------------------------
     */

    public record Match(
            String screen,
            String menu,
            String title,
            int slotCount,
            int minimumSlotCount,
            int maximumSlotCount
    ) {

        public static final Codec<Match> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "screen",
                                                        ""
                                                )
                                                .forGetter(
                                                        Match::screen
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "menu",
                                                        ""
                                                )
                                                .forGetter(
                                                        Match::menu
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "title",
                                                        ""
                                                )
                                                .forGetter(
                                                        Match::title
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "slot_count",
                                                        0
                                                )
                                                .forGetter(
                                                        Match::slotCount
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "minimum_slot_count",
                                                        0
                                                )
                                                .forGetter(
                                                        Match::minimumSlotCount
                                                ),

                                        Codec.INT
                                                .optionalFieldOf(
                                                        "maximum_slot_count",
                                                        0
                                                )
                                                .forGetter(
                                                        Match::maximumSlotCount
                                                )
                                ).apply(
                                        instance,
                                        Match::new
                                )
                );

        public Match {

            screen =
                    normalize(
                            screen
                    );

            menu =
                    normalize(
                            menu
                    );

            title =
                    normalize(
                            title
                    );

            slotCount =
                    Math.max(
                            0,
                            slotCount
                    );

            minimumSlotCount =
                    Math.max(
                            0,
                            minimumSlotCount
                    );

            maximumSlotCount =
                    Math.max(
                            0,
                            maximumSlotCount
                    );

            if (
                    minimumSlotCount > 0
                            && maximumSlotCount > 0
                            && maximumSlotCount < minimumSlotCount
            ) {

                throw new IllegalArgumentException(
                        "maximum_slot_count cannot be less than minimum_slot_count"
                );
            }
        }

        public static Match empty() {

            return new Match(
                    "",
                    "",
                    "",
                    0,
                    0,
                    0
            );
        }

        public boolean hasScreenRequirement() {

            return !screen.isBlank();
        }

        public boolean hasMenuRequirement() {

            return !menu.isBlank();
        }

        public boolean hasTitleRequirement() {

            return !title.isBlank();
        }

        public boolean hasExactSlotCount() {

            return slotCount > 0;
        }

        public boolean hasMinimumSlotCount() {

            return minimumSlotCount > 0;
        }

        public boolean hasMaximumSlotCount() {

            return maximumSlotCount > 0;
        }

        public boolean isFallback() {

            return !hasScreenRequirement()
                    && !hasMenuRequirement()
                    && !hasTitleRequirement()
                    && !hasExactSlotCount()
                    && !hasMinimumSlotCount()
                    && !hasMaximumSlotCount();
        }
    }

    /*
     * ------------------------------------------------------------
     * CONTROL
     * ------------------------------------------------------------
     */

    /**
     * Normalized screen-space control.
     *
     * x/y range:
     *
     * 0.0 = left/top
     * 1.0 = right/bottom
     *
     * These are only a fallback for screens that cannot be manipulated
     * through logical container slots.
     */
    public record Control(
            double x,
            double y,
            String button
    ) {

        public static final Codec<Control> CODEC =
                RecordCodecBuilder.create(
                        instance ->
                                instance.group(
                                        Codec.DOUBLE
                                                .fieldOf(
                                                        "x"
                                                )
                                                .forGetter(
                                                        Control::x
                                                ),

                                        Codec.DOUBLE
                                                .fieldOf(
                                                        "y"
                                                )
                                                .forGetter(
                                                        Control::y
                                                ),

                                        Codec.STRING
                                                .optionalFieldOf(
                                                        "button",
                                                        "left"
                                                )
                                                .forGetter(
                                                        Control::button
                                                )
                                ).apply(
                                        instance,
                                        Control::new
                                )
                );

        public Control {

            x =
                    clamp01(
                            x
                    );

            y =
                    clamp01(
                            y
                    );

            button =
                    normalize(
                            button
                    )
                            .toLowerCase();

            if (button.isBlank()) {

                button =
                        "left";
            }

            if (
                    !button.equals(
                            "left"
                    )
                            && !button.equals(
                            "right"
                    )
                            && !button.equals(
                            "middle"
                    )
            ) {

                throw new IllegalArgumentException(
                        "Unsupported screen control button '"
                                + button
                                + "'"
                );
            }
        }

        public String resolvedButton() {

            return button;
        }

        public int mouseButton() {

            return switch (button) {

                case "right" ->
                        1;

                case "middle" ->
                        2;

                default ->
                        0;
            };
        }
    }

    /*
     * ------------------------------------------------------------
     * HELPERS
     * ------------------------------------------------------------
     */

    private static String normalizeKey(
            String value
    ) {

        return normalize(
                value
        )
                .toLowerCase();
    }

    private static String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private static double clamp01(
            double value
    ) {

        if (value < 0.0D) {
            return 0.0D;
        }

        if (value > 1.0D) {
            return 1.0D;
        }

        return value;
    }
}