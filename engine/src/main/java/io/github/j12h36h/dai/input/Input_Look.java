package io.github.j12h36h.dai.input;

public final class Input_Look {

    private float yaw;
    private float pitch;

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}