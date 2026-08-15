package io.github.j12h36h.dai.client.logics.input;

public final class DAI_InputLook {

    private float yaw;
    private float pitch;

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void addRotation(
            float yaw,
            float pitch
    ) {

        setRotation(
                this.yaw + yaw,
                this.pitch + pitch
        );
    }
}
