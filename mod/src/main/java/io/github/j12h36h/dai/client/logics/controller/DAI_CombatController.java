package io.github.j12h36h.dai.client.logics.controller;

import io.github.j12h36h.dai.client.logics.approach.DAI_ApproachProfile;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.client.logics.input.DAI_InputState;
import io.github.j12h36h.dai.client.menus.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class DAI_CombatController {

    private static final int HELD_ATTACK_COOLDOWN =
            10;

    /*
     * Number of consecutive ticks during which the target may remain
     * meaningfully farther away before combat treats the pursuit as
     * unstable.
     */
    private static final int MOVING_AWAY_TOLERANCE_TICKS =
            20;

    /*
     * Small distance changes are normal entity movement and player
     * movement. Do not count them as meaningful target displacement.
     */
    private static final double DISTANCE_CHANGE_EPSILON =
            0.15D;

    private static boolean attackPending;
    private static boolean attackHeld;

    private static int attackCooldown;

    private static LivingEntity combatTarget;

    private static DAI_ApproachProfile combatProfile;

    private static boolean combatActive;

    private static double previousTargetDistance =
            Double.NaN;

    private static Vec3 previousTargetPosition;

    private static int movingAwayTicks;

    private static int cancelledApproaches;

    private DAI_CombatController() {
        // Utility class.
    }

    /**
     * Requests a normal one-shot attack against whatever entity is
     * currently under the player's crosshair.
     */
    public static void requestAttack() {

        attackPending =
                true;

        DAI_Core.debug(
                "<DAI>: Basic attack requested."
        );
    }

    /**
     * Begins repeatedly attacking the current crosshair target whenever
     * the player's attack strength has recovered.
     */
    public static void startAttack() {

        attackHeld =
                true;

        DAI_Core.debug(
                "<DAI>: Held attack started."
        );
    }

    /**
     * Stops held crosshair attacks.
     */
    public static void stopAttack() {

        attackHeld =
                false;

        DAI_Core.debug(
                "<DAI>: Held attack stopped."
        );
    }

    /**
     * Begins an autonomous engagement against a living entity.
     *
     * Recognition and initial target selection have already happened by
     * this point, but combat performs its own pursuit-radius validation
     * so stale targets cannot start an unreasonable chase.
     */
    public static void engage(
            LivingEntity target
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot engage without an active player and level."
            );

            return;
        }

        if (
                target == null
                        || target.isRemoved()
                        || !target.isAlive()
        ) {

            DAI_Core.LOGGER.warn(
                    "<DAI>: Cannot engage an invalid combat target."
            );

            return;
        }

        DAI_ApproachProfile profile =
                DAI_ApproachProfile.forEntity(
                        target
                );

        double distance =
                minecraft.player.distanceTo(
                        target
                );

        if (
                distance
                        > profile.pursuitRadius()
        ) {

            DAI_Core.debug(
                    "<DAI>: Refused combat engagement against '{}' category={} because distance={} exceeds pursuitRadius={}.",
                    target.getName().getString(),
                    profile.category(),
                    formatDistance(
                            distance
                    ),
                    formatDistance(
                            profile.pursuitRadius()
                    )
            );

            if (
                    DAI_TargetState.selected()
                            == target
            ) {

                DAI_TargetState.clearEntity();
            }

            return;
        }

        /*
         * Long-range navigation must release movement and rotation
         * ownership before combat begins.
         */
        if (
                DAI_PathController.isActive()
        ) {

            DAI_PathController.stop();
        }

        /*
         * Block approach movement should not compete with combat.
         */
        if (
                DAI_ApproachController.isActive()
        ) {

            DAI_ApproachController.stop();
        }

        combatTarget =
                target;

        combatProfile =
                profile;

        combatActive =
                true;

        attackPending =
                false;

        attackCooldown =
                0;

        previousTargetDistance =
                distance;

        previousTargetPosition =
                target.position();

        movingAwayTicks =
                0;

        cancelledApproaches =
                0;

        DAI_InputState.setManagedOverride(
                true
        );

        DAI_Core.debug(
                "<DAI>: Began combat engagement against '{}' category={} distance={} pursuitRadius={} interactionDistance={}.",
                target.getName().getString(),
                profile.category(),
                formatDistance(
                        distance
                ),
                formatDistance(
                        profile.pursuitRadius()
                ),
                formatDistance(
                        profile.interactionDistance()
                )
        );
    }

    /**
     * Called every client tick.
     */
    public static void tick() {

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        /*
         * Autonomous targeted combat has priority over the basic
         * crosshair attack modes.
         */
        if (combatActive) {

            tickCombat();

            return;
        }

        tickBasicAttack();
    }

    private static void tickCombat() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.level == null
                        || minecraft.gameMode == null
        ) {

            finishCombat(
                    "player, level, or game mode became unavailable"
            );

            return;
        }

        if (
                combatTarget == null
                        || combatTarget.isRemoved()
                        || !combatTarget.isAlive()
        ) {

            finishCombat(
                    "target defeated or became unavailable"
            );

            return;
        }

        if (combatProfile == null) {

            combatProfile =
                    DAI_ApproachProfile.forEntity(
                            combatTarget
                    );
        }

        /*
         * Make sure another navigation action did not start while this
         * engagement was already active.
         *
         * Count repeated ownership conflicts. If something repeatedly
         * tries to replace combat movement, abandon this engagement
         * rather than endlessly fighting controller ownership.
         */
        if (
                DAI_PathController.isActive()
        ) {

            DAI_PathController.stop();

            cancelledApproaches++;

            DAI_InputState.setManagedOverride(
                    true
            );
        }

        if (
                DAI_ApproachController.isActive()
        ) {

            DAI_ApproachController.stop();

            cancelledApproaches++;

            DAI_InputState.setManagedOverride(
                    true
            );
        }

        if (
                cancelledApproaches
                        > combatProfile.maxCancelledApproaches()
        ) {

            finishCombat(
                    "too many competing navigation approaches"
            );

            return;
        }

        double distance =
                minecraft.player.distanceTo(
                        combatTarget
                );

        /*
         * Pursuit distance is intentionally separate from recognition
         * distance. Once the entity leaves the profile's pursuit radius,
         * DAI abandons it instead of chasing indefinitely.
         */
        if (
                distance
                        > combatProfile.pursuitRadius()
        ) {

            finishCombat(
                    "target left pursuit radius"
            );

            return;
        }

        if (
                targetMovementExceededTolerance(
                        combatTarget,
                        combatProfile
                )
        ) {

            /*
             * A highly mobile target has moved far enough that our old
             * pursuit assumptions are no longer trustworthy. Reset the
             * distance trend and continue from its current position.
             *
             * This is deliberately not an immediate failure because
             * ordinary passive and hostile mobs move constantly.
             */
            previousTargetDistance =
                    distance;

            previousTargetPosition =
                    combatTarget.position();

            movingAwayTicks =
                    0;

            DAI_Core.debug(
                    "<DAI>: Revalidated moving combat target '{}' after displacement exceeded {} block(s).",
                    combatTarget.getName().getString(),
                    formatDistance(
                            combatProfile.movingTargetTolerance()
                    )
            );
        }

        updateMovingAwayConfidence(
                distance
        );

        if (
                movingAwayTicks
                        >= MOVING_AWAY_TOLERANCE_TICKS
        ) {

            finishCombat(
                    "target continued moving away"
            );

            return;
        }

        faceTarget(
                minecraft,
                combatTarget
        );

        /*
         * The profile defines the final interaction distance.
         *
         * Passive and neutral mobs can use tighter commitment distances,
         * while bosses and other categories may use different values
         * without changing combat-controller code.
         */
        if (
                distance
                        > combatProfile.interactionDistance()
        ) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            1.0F,
                            0.0F
                    );

            previousTargetDistance =
                    distance;

            return;
        }

        /*
         * We are inside the profile's interaction distance. Stop
         * advancing so we do not walk through or circle the target.
         */
        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        previousTargetDistance =
                distance;

        if (attackCooldown > 0) {
            return;
        }

        float strength =
                minecraft.player
                        .getAttackStrengthScale(
                                0.0F
                        );

        if (strength < 0.95F) {
            return;
        }

        /*
         * Re-facing every tick should normally place the target under
         * the crosshair. Still verify the actual hit result before
         * sending the attack.
         */
        HitResult hitResult =
                minecraft.hitResult;

        if (
                !(
                        hitResult
                                instanceof EntityHitResult entityHitResult
                )
        ) {
            return;
        }

        if (
                entityHitResult.getEntity()
                        != combatTarget
        ) {
            return;
        }

        minecraft.gameMode.attack(
                minecraft.player,
                combatTarget
        );

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        attackCooldown =
                HELD_ATTACK_COOLDOWN;

        DAI_Core.debug(
                "<DAI>: Attacked combat target '{}'.",
                combatTarget.getName().getString()
        );
    }

    /**
     * Tracks whether the target is consistently getting farther away.
     *
     * A few ticks of increasing distance are normal. Sustained movement
     * away from the player indicates that continuing pursuit is becoming
     * increasingly speculative.
     */
    private static void updateMovingAwayConfidence(
            double currentDistance
    ) {

        if (
                Double.isNaN(
                        previousTargetDistance
                )
        ) {

            previousTargetDistance =
                    currentDistance;

            movingAwayTicks =
                    0;

            return;
        }

        if (
                currentDistance
                        > previousTargetDistance
                        + DISTANCE_CHANGE_EPSILON
        ) {

            movingAwayTicks++;

            return;
        }

        /*
         * Recover confidence gradually instead of resetting immediately.
         * This prevents a single favorable movement tick from erasing a
         * long trend of the target escaping.
         */
        if (movingAwayTicks > 0) {
            movingAwayTicks--;
        }
    }

    /**
     * Determines whether the entity itself has moved far enough from the
     * last reference position that its pursuit assumptions should be
     * refreshed.
     */
    private static boolean targetMovementExceededTolerance(
            LivingEntity target,
            DAI_ApproachProfile profile
    ) {

        if (
                target == null
                        || profile == null
        ) {
            return false;
        }

        Vec3 currentPosition =
                target.position();

        if (previousTargetPosition == null) {

            previousTargetPosition =
                    currentPosition;

            return false;
        }

        double moved =
                currentPosition.distanceTo(
                        previousTargetPosition
                );

        if (
                moved
                        <= profile.movingTargetTolerance()
        ) {
            return false;
        }

        previousTargetPosition =
                currentPosition;

        return true;
    }

    private static void tickBasicAttack() {

        if (
                attackHeld
                        && attackCooldown > 0
        ) {
            return;
        }

        if (
                !attackPending
                        && !attackHeld
        ) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (
                minecraft.player == null
                        || minecraft.gameMode == null
        ) {
            return;
        }

        if (attackHeld) {

            float strength =
                    minecraft.player
                            .getAttackStrengthScale(
                                    0.0F
                            );

            if (strength < 0.95F) {
                return;
            }
        }

        attackPending =
                false;

        HitResult hitResult =
                minecraft.hitResult;

        if (
                hitResult
                        instanceof EntityHitResult entityHitResult
        ) {

            minecraft.gameMode.attack(
                    minecraft.player,
                    entityHitResult.getEntity()
            );
        }

        minecraft.player.swing(
                InteractionHand.MAIN_HAND
        );

        attackCooldown =
                HELD_ATTACK_COOLDOWN;

        DAI_Core.debug(
                "<DAI>: Performed held/basic attack; cooldown={} tick(s).",
                attackHeld
                        ? HELD_ATTACK_COOLDOWN
                        : 0
        );
    }

    private static void faceTarget(
            Minecraft minecraft,
            LivingEntity target
    ) {

        if (
                minecraft.player == null
                        || target == null
        ) {
            return;
        }

        Vec3 eyePosition =
                minecraft.player
                        .getEyePosition();

        Vec3 targetPosition =
                target.getEyePosition();

        double deltaX =
                targetPosition.x
                        - eyePosition.x;

        double deltaY =
                targetPosition.y
                        - eyePosition.y;

        double deltaZ =
                targetPosition.z
                        - eyePosition.z;

        double horizontal =
                Math.sqrt(
                        deltaX * deltaX
                                + deltaZ * deltaZ
                );

        float yaw =
                (float) (
                        Math.toDegrees(
                                Math.atan2(
                                        deltaZ,
                                        deltaX
                                )
                        )
                                - 90.0D
                );

        float pitch =
                (float) (
                        -Math.toDegrees(
                                Math.atan2(
                                        deltaY,
                                        horizontal
                                )
                        )
                );

        float finalYaw =
                Mth.wrapDegrees(
                        yaw
                );

        float finalPitch =
                Mth.clamp(
                        pitch,
                        -90.0F,
                        90.0F
                );

        DAI_InputState
                .look()
                .setRotation(
                        finalYaw,
                        finalPitch
                );
    }

    private static void finishCombat(
            String reason
    ) {

        LivingEntity finishedTarget =
                combatTarget;

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        combatTarget =
                null;

        combatProfile =
                null;

        combatActive =
                false;

        attackCooldown =
                0;

        previousTargetDistance =
                Double.NaN;

        previousTargetPosition =
                null;

        movingAwayTicks =
                0;

        cancelledApproaches =
                0;

        DAI_InputState.setManagedOverride(
                false
        );

        /*
         * Clear the selected entity if it was the entity this combat
         * engagement was using.
         */
        if (
                finishedTarget != null
                        && DAI_TargetState.selected()
                        == finishedTarget
        ) {

            DAI_TargetState.clearEntity();
        }

        DAI_Core.debug(
                "<DAI>: Combat engagement finished: {}.",
                reason
        );
    }

    public static void stopCombat() {

        if (!combatActive) {
            return;
        }

        finishCombat(
                "stopped manually"
        );
    }

    public static boolean isCombatActive() {
        return combatActive;
    }

    public static LivingEntity combatTarget() {
        return combatTarget;
    }

    public static void reset() {

        attackPending =
                false;

        attackHeld =
                false;

        attackCooldown =
                0;

        combatTarget =
                null;

        combatProfile =
                null;

        combatActive =
                false;

        previousTargetDistance =
                Double.NaN;

        previousTargetPosition =
                null;

        movingAwayTicks =
                0;

        cancelledApproaches =
                0;

        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

        DAI_InputState.setManagedOverride(
                false
        );
    }

    private static String formatDistance(
            double distance
    ) {

        return String.format(
                Locale.ROOT,
                "%.2f",
                distance
        );
    }
}