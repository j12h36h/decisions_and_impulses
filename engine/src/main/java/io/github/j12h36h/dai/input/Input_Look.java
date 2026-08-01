package io.github.j12h36h.dai.input;

public final class Input_Look {

    private float yaw;
    private float pitch;

    /**
     * Sets the desired rotation.
     */
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /**
     * Sets the desired yaw.
     */
    public void yaw(float yaw) {
        this.yaw = yaw;
    }

    /**
     * Sets the desired pitch.
     */
    public void pitch(float pitch) {
        this.pitch = pitch;
    }

    /**
     * Returns the desired yaw.
     */
    public float yaw() {
        return yaw;
    }

    /**
     * Returns the desired pitch.
     */
    public float pitch() {
        return pitch;
    }

    /**
     * Resets the desired rotation.
     */
    public void clear() {
        yaw = 0.0F;
        pitch = 0.0F;
    }
}