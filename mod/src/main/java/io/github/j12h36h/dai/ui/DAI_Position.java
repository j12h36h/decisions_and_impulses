package io.github.j12h36h.dai.ui;

public enum DAI_Position {

    TOP_LEFT("Top Left"),
    MID_LEFT("Middle Left"),
    BOT_LEFT("Bottom Left"),

    TOP_CENTER("Top Center"),
    BOT_CENTER("Bottom Center"),

    TOP_RIGHT("Top Right"),
    MID_RIGHT("Middle Right"),
    BOT_RIGHT("Bottom Right");

    private final String displayName;

    DAI_Position(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}