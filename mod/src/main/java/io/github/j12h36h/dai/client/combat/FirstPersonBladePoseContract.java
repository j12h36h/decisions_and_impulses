package io.github.j12h36h.dai.client.combat;

/**
 * Screen-space target poses used by Musashi-style directional blade combat.
 *
 * Direction ids deliberately match the Musashi datapack contract:
 * 1 up, 2 up-right, 3 right, 4 down-right, 5 down,
 * 6 down-left, 7 left, 8 up-left, 9 thrust/stab.
 */
public final class FirstPersonBladePoseContract {

    public record Pose(
            float pitch,
            float yaw,
            float roll,
            float x,
            float y,
            float z
    ) {}

    private static final Pose READY =
            new Pose(-18.0F, 0.0F, -28.0F, 0.0F, 0.0F, -0.10F);

    private static final Pose GUARD =
            new Pose(-24.0F, 12.0F, -42.0F, 0.08F, -0.04F, -0.18F);

    private FirstPersonBladePoseContract() {
        // Utility class.
    }

    public static Pose ready() {
        return READY;
    }

    public static Pose guard() {
        return GUARD;
    }

    public static Pose pose(int direction) {
        return switch (direction) {
            case 1 -> new Pose(-58, 0,   0, -0.08F,  0.14F, -0.16F); // up
            case 2 -> new Pose(-40, 0, -48,  0.10F,  0.12F, -0.18F); // up-right
            case 3 -> new Pose( -4, 0, -84,  0.20F,  0.02F, -0.19F); // right
            case 4 -> new Pose( 38, 0, -50,  0.12F, -0.10F, -0.18F); // down-right
            case 5 -> new Pose( 60, 0,   0, -0.05F, -0.15F, -0.15F); // down
            case 6 -> new Pose( 38, 0,  50, -0.18F, -0.10F, -0.17F); // down-left
            case 7 -> new Pose( -4, 0,  84, -0.23F,  0.02F, -0.18F); // left
            case 8 -> new Pose(-40, 0,  48, -0.16F,  0.12F, -0.16F); // up-left
            case 9 -> new Pose( 78, 0,   0,  0.00F, -0.03F, -0.52F); // thrust
            default -> READY;
        };
    }

    public static int opposite(int direction) {
        return switch (direction) {
            case 1 -> 5;
            case 2 -> 6;
            case 3 -> 7;
            case 4 -> 8;
            case 5 -> 1;
            case 6 -> 2;
            case 7 -> 3;
            case 8 -> 4;
            default -> 0;
        };
    }
}
