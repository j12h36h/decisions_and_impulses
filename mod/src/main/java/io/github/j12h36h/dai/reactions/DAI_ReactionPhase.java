package io.github.j12h36h.dai.reactions;

import java.util.Locale;

public enum DAI_ReactionPhase {

    PRE("pre"),
    DURING("during"),
    POST("post"),
    UNKNOWN("unknown");

    private final String id;

    DAI_ReactionPhase(
            String id
    ) {

        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DAI_ReactionPhase parse(
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

            case "pre" ->
                    PRE;

            case "during" ->
                    DURING;

            case "post" ->
                    POST;

            default ->
                    UNKNOWN;
        };
    }
}
