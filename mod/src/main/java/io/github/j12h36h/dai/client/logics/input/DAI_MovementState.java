package io.github.j12h36h.dai.client.logics.input;

public final class DAI_MovementState {

    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public void setMovement(float forward, float strafe) {
        this.forward = clamp(forward);
        this.strafe = clamp(strafe);
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

    public float forward() {
        return forward;
    }

    public float strafe() {
        return strafe;
    }

    public boolean jump() {
        return jump;
    }

    public boolean sneak() {
        return sneak;
    }

    public boolean sprint() {
        return sprint;
    }

    public void clearMovement() {
        forward = 0.0F;
        strafe = 0.0F;
        jump = false;
    }

    public void clear() {
        clearMovement();
        sneak = false;
        sprint = false;
    }

    private static float clamp(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
