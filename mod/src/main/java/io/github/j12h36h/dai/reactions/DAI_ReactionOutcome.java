package io.github.j12h36h.dai.reactions;

public enum DAI_ReactionOutcome {

    PASS,
    OVERRIDE,
    CANCEL;

    public boolean stopsUnderlyingEvent() {

        return this == OVERRIDE
                || this == CANCEL;
    }
}
