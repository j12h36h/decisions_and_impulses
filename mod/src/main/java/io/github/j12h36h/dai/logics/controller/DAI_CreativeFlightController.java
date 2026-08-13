package io.github.j12h36h.dai.logics.controller;

import io.github.j12h36h.dai.logics.action.DAI_ActionResult;
import io.github.j12h36h.dai.logics.action.DAI_ActionStatus;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.logics.input.DAI_InputState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DAI_CreativeFlightController {

    private static final double VERTICAL_DEADZONE = 0.30D;
    private static final double HORIZONTAL_DEADZONE = 0.12D;
    private static final double PROGRESS_EPSILON = 0.035D;
    private static final double DISTANCE_PROGRESS_EPSILON = 0.045D;

    /*
     * Direct vanilla/synthetic flight remains the fast path. If geometry stops
     * progress, do not keep pushing straight through the obstruction. Recover
     * using a Minecraft-like three-leg route: rise into clear air, traverse
     * horizontally above the local build, then descend to the requested stance.
     */
    private static final int RECOVERY_AFTER_STALLED_TICKS = 12;
    private static final int VELOCITY_ASSIST_AFTER_RECOVERY_TICKS = 10;
    private static final int HARD_STALL_TICKS = 80;
    private static final double RECOVERY_CLEARANCE = 3.50D;
    private static final double RECOVERY_SEGMENT_TOLERANCE = 0.45D;
    private static final double ASSIST_HORIZONTAL_SPEED = 0.18D;
    private static final double ASSIST_VERTICAL_SPEED = 0.14D;

    private enum FlightPhase {
        DIRECT,
        RECOVERY_ASCEND,
        RECOVERY_TRAVERSE,
        RECOVERY_DESCEND
    }

    private static boolean active;
    /** Final destination requested by the action. */
    private static Vec3 target;
    /** Current segment destination; equals target during normal direct flight. */
    private static Vec3 movementTarget;
    private static double tolerance;
    private static int remainingTicks;
    private static int generation;
    private static int completedGeneration;
    private static DAI_ActionResult completedResult = DAI_ActionResult.SUCCESS;

    private static Vec3 lastProgressPosition;
    private static double bestDistance;
    private static int stalledTicks;
    private static boolean velocityAssistActive;
    private static FlightPhase phase = FlightPhase.DIRECT;
    private static double recoverySafeY;

    private DAI_CreativeFlightController() {
        // Utility class.
    }

    public static int start(Vec3 destination, int ticks, double requestedTolerance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || destination == null) {
            finish(DAI_ActionResult.FAILURE);
            return generation;
        }

        if (!minecraft.player.getAbilities().mayfly) {
            finish(DAI_ActionResult.FAILURE);
            DAI_Core.LOGGER.warn("<DAI>: Creative flight requested but the player may not fly.");
            return generation;
        }

        minecraft.player.getAbilities().flying = true;
        minecraft.player.onUpdateAbilities();

        generation = generation == Integer.MAX_VALUE ? 1 : generation + 1;
        target = destination;
        movementTarget = destination;
        tolerance = requestedTolerance > 0.0D ? requestedTolerance : 0.55D;
        remainingTicks = ticks > 0 ? ticks : 200;
        active = true;
        phase = FlightPhase.DIRECT;
        recoverySafeY = Double.NaN;

        resetProgress(minecraft.player.position(), movementTarget);
        velocityAssistActive = false;

        DAI_InputState.movement().clear();
        DAI_InputState.setManagedOverride(true);
        DAI_ActionStatus.set(DAI_ActionResult.RUNNING);

        DAI_Core.debug(
                "<DAI>: Creative flight generation={} target={} tolerance={} ticks={}.",
                generation,
                destination,
                tolerance,
                remainingTicks
        );
        return generation;
    }

    public static void tick() {
        if (!active) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getAbilities().mayfly) {
            finish(DAI_ActionResult.FAILURE);
            return;
        }

        if (!minecraft.player.getAbilities().flying) {
            minecraft.player.getAbilities().flying = true;
            minecraft.player.onUpdateAbilities();
        }

        Vec3 position = minecraft.player.position();
        if (target == null || movementTarget == null) {
            finish(DAI_ActionResult.FAILURE);
            return;
        }

        double segmentTolerance =
                phase == FlightPhase.DIRECT || phase == FlightPhase.RECOVERY_DESCEND
                        ? tolerance
                        : Math.max(tolerance, RECOVERY_SEGMENT_TOLERANCE);

        double segmentDistance = position.distanceTo(movementTarget);
        if (segmentDistance <= segmentTolerance) {
            if (phase == FlightPhase.DIRECT || phase == FlightPhase.RECOVERY_DESCEND) {
                finish(DAI_ActionResult.SUCCESS);
                return;
            }

            advanceRecovery(position);
            if (!active) return;
            segmentDistance = position.distanceTo(movementTarget);
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            finish(DAI_ActionResult.TIMED_OUT);
            return;
        }

        Vec3 delta = movementTarget.subtract(position);
        driveInput(minecraft, delta);
        updateProgress(position, segmentDistance);

        if (phase == FlightPhase.DIRECT
                && stalledTicks >= RECOVERY_AFTER_STALLED_TICKS) {
            beginRecovery(position);
            return;
        }

        if (phase != FlightPhase.DIRECT
                && stalledTicks >= VELOCITY_ASSIST_AFTER_RECOVERY_TICKS) {
            velocityAssistActive = true;
        }

        if (velocityAssistActive) {
            applyVelocityAssist(minecraft, movementTarget.subtract(position));
        }

        if (stalledTicks >= HARD_STALL_TICKS) {
            DAI_Core.LOGGER.warn(
                    "<DAI>: Creative flight generation={} phase={} stalled at {} blocks from segment target {} (final {}).",
                    generation,
                    phase,
                    String.format(java.util.Locale.ROOT, "%.3f", segmentDistance),
                    movementTarget,
                    target
            );
            finish(DAI_ActionResult.FAILURE);
        }
    }

    private static void beginRecovery(Vec3 position) {
        recoverySafeY = Math.max(position.y, target.y) + RECOVERY_CLEARANCE;
        phase = FlightPhase.RECOVERY_ASCEND;
        movementTarget = new Vec3(position.x, recoverySafeY, position.z);
        velocityAssistActive = false;
        resetProgress(position, movementTarget);

        DAI_Core.debug(
                "<DAI>: Creative flight generation={} entering obstruction recovery via y={}.",
                generation,
                String.format(java.util.Locale.ROOT, "%.3f", recoverySafeY)
        );
    }

    private static void advanceRecovery(Vec3 position) {
        switch (phase) {
            case RECOVERY_ASCEND -> {
                phase = FlightPhase.RECOVERY_TRAVERSE;
                movementTarget = new Vec3(target.x, recoverySafeY, target.z);
                velocityAssistActive = false;
                resetProgress(position, movementTarget);
            }
            case RECOVERY_TRAVERSE -> {
                phase = FlightPhase.RECOVERY_DESCEND;
                movementTarget = target;
                velocityAssistActive = false;
                resetProgress(position, movementTarget);
            }
            case RECOVERY_DESCEND, DIRECT -> finish(DAI_ActionResult.SUCCESS);
        }
    }

    private static void driveInput(
            Minecraft minecraft,
            Vec3 delta
    ) {
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = minecraft.player.getYRot();
        float pitch = 0.0F;

        if (horizontal > HORIZONTAL_DEADZONE) {
            yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D);
            yaw = Mth.wrapDegrees(yaw);
            pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
            pitch = Math.clamp(pitch, -75.0F, 75.0F);

            DAI_InputState.look().setRotation(yaw, pitch);
            DAI_InputState.movement().setMovement(1.0F, 0.0F);
        } else {
            DAI_InputState.movement().setMovement(0.0F, 0.0F);
            if (Math.abs(delta.y) > VERTICAL_DEADZONE) {
                pitch = delta.y > 0.0D ? -90.0F : 90.0F;
                DAI_InputState.look().setRotation(yaw, pitch);
            }
        }

        if (delta.y > VERTICAL_DEADZONE) {
            DAI_InputState.movement().setJump(true);
            DAI_InputState.movement().setSneak(false);
        } else if (delta.y < -VERTICAL_DEADZONE) {
            DAI_InputState.movement().setJump(false);
            DAI_InputState.movement().setSneak(true);
        } else {
            DAI_InputState.movement().setJump(false);
            DAI_InputState.movement().setSneak(false);
        }
    }

    private static void resetProgress(Vec3 position, Vec3 destination) {
        lastProgressPosition = position;
        bestDistance = destination == null || position == null
                ? Double.POSITIVE_INFINITY
                : destination.distanceTo(position);
        stalledTicks = 0;
    }

    private static void updateProgress(
            Vec3 position,
            double distance
    ) {
        boolean positionProgress =
                lastProgressPosition == null
                        || position.distanceTo(lastProgressPosition) >= PROGRESS_EPSILON;

        boolean distanceProgress =
                !Double.isFinite(bestDistance)
                        || distance <= bestDistance - DISTANCE_PROGRESS_EPSILON;

        if (positionProgress || distanceProgress) {
            stalledTicks = 0;
            lastProgressPosition = position;
            bestDistance = Math.min(bestDistance, distance);
            return;
        }

        stalledTicks++;
    }

    private static void applyVelocityAssist(
            Minecraft minecraft,
            Vec3 delta
    ) {
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double x = 0.0D;
        double z = 0.0D;

        if (horizontal > HORIZONTAL_DEADZONE) {
            x = delta.x / horizontal * ASSIST_HORIZONTAL_SPEED;
            z = delta.z / horizontal * ASSIST_HORIZONTAL_SPEED;
        }

        double y;
        if (delta.y > VERTICAL_DEADZONE) {
            y = Math.min(ASSIST_VERTICAL_SPEED, delta.y * 0.20D);
        } else if (delta.y < -VERTICAL_DEADZONE) {
            y = Math.max(-ASSIST_VERTICAL_SPEED, delta.y * 0.20D);
        } else {
            y = 0.0D;
        }

        minecraft.player.setDeltaMovement(new Vec3(x, y, z));
    }

    private static void finish(DAI_ActionResult result) {
        Minecraft minecraft = Minecraft.getInstance();

        if (active) {
            completedGeneration = generation;
            completedResult = result;
        }

        active = false;
        target = null;
        movementTarget = null;
        remainingTicks = 0;
        stalledTicks = 0;
        lastProgressPosition = null;
        bestDistance = Double.POSITIVE_INFINITY;
        velocityAssistActive = false;
        phase = FlightPhase.DIRECT;
        recoverySafeY = Double.NaN;

        DAI_InputState.movement().clear();
        DAI_InputState.setManagedOverride(false);

        if (minecraft.player != null && minecraft.player.getAbilities().flying) {
            minecraft.player.setDeltaMovement(Vec3.ZERO);
        }

        DAI_ActionStatus.set(result);
    }

    public static void reset() {
        Minecraft minecraft = Minecraft.getInstance();

        active = false;
        target = null;
        movementTarget = null;
        remainingTicks = 0;
        stalledTicks = 0;
        lastProgressPosition = null;
        bestDistance = Double.POSITIVE_INFINITY;
        velocityAssistActive = false;
        phase = FlightPhase.DIRECT;
        recoverySafeY = Double.NaN;
        DAI_InputState.movement().clear();
        DAI_InputState.setManagedOverride(false);

        if (minecraft.player != null && minecraft.player.getAbilities().flying) {
            minecraft.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int generation() {
        return generation;
    }

    public static Vec3 target() {
        return target;
    }

    public static int remainingTicks() {
        return remainingTicks;
    }

    public static int stalledTicks() {
        return stalledTicks;
    }

    public static boolean velocityAssistActive() {
        return velocityAssistActive;
    }

    public static String phaseName() {
        return phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static double distanceToTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || target == null || minecraft.player == null) {
            return -1.0D;
        }
        return minecraft.player.position().distanceTo(target);
    }

    public static double distanceToMovementTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || movementTarget == null || minecraft.player == null) {
            return -1.0D;
        }
        return minecraft.player.position().distanceTo(movementTarget);
    }

    public static DAI_ActionResult resultForGeneration(int requested) {
        if (active && requested == generation) return DAI_ActionResult.RUNNING;
        if (requested == completedGeneration) return completedResult;
        return DAI_ActionResult.FAILURE;
    }
}
