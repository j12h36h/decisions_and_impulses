package io.github.j12h36h.dai.reactions;

import java.util.Locale;

public enum DAI_ReactionType {

    DEFAULT("default"),
    OVERRIDE("override"),
    CANCEL("cancel"),
    UNKNOWN("unknown");

    private final String id;

    DAI_ReactionType(
            String id
    ) {

        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DAI_ReactionType parse(
            String value
    ) {

        String normalized =
                value == null
                        ? ""
                        : value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return switch (normalized) {

            case "default" ->
                    DEFAULT;

            case "override" ->
                    OVERRIDE;

            case "cancel" ->
                    CANCEL;

            default ->
                    UNKNOWN;
        };
    }
}
