package io.github.j12h36h.dai.controller;

import io.github.j12h36h.dai.core.DAI_Core;
import io.github.j12h36h.dai.input.DAI_InputState;
import io.github.j12h36h.dai.system.DAI_TargetState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DAI_CombatController {

    private static final int HELD_ATTACK_COOLDOWN =
            10;

    /*
     * Stop slightly inside normal melee reach so the player does not
     * constantly oscillate between moving and stopping.
     */
    private static final double MELEE_DISTANCE =
            2.75D;

    private static boolean attackPending;
    private static boolean attackHeld;

    private static int attackCooldown;

    private static LivingEntity combatTarget;

    private static boolean combatActive;

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

        DAI_Core.LOGGER.debug(
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

        DAI_Core.LOGGER.debug(
                "<DAI>: Held attack started."
        );
    }

    /**
     * Stops held crosshair attacks.
     */
    public static void stopAttack() {

        attackHeld =
                false;

        DAI_Core.LOGGER.debug(
                "<DAI>: Held attack stopped."
        );
    }

    /**
     * Begins an autonomous melee engagement against a living entity.
     */
    public static void engage(
            LivingEntity target
    ) {

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
         * Block approach movement should not compete with combat either.
         */
        if (
                DAI_ApproachController.isActive()
        ) {

            DAI_ApproachController.stop();
        }

        combatTarget =
                target;

        combatActive =
                true;

        attackPending =
                false;

        attackCooldown =
                0;

        DAI_InputState.setManagedOverride(
                true
        );

        DAI_Core.LOGGER.debug(
                "<DAI>: Began combat engagement against '{}'.",
                target.getName().getString()
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

        /*
         * Make sure another navigation action did not start while this
         * engagement was already active.
         */
        if (
                DAI_PathController.isActive()
        ) {

            DAI_PathController.stop();

            DAI_InputState.setManagedOverride(
                    true
            );
        }

        if (
                DAI_ApproachController.isActive()
        ) {

            DAI_ApproachController.stop();

            DAI_InputState.setManagedOverride(
                    true
            );
        }

        faceTarget(
                minecraft,
                combatTarget
        );

        double distance =
                minecraft.player.distanceTo(
                        combatTarget
                );

        if (
                distance
                        > MELEE_DISTANCE
        ) {

            DAI_InputState
                    .movement()
                    .setMovement(
                            1.0F,
                            0.0F
                    );

            return;
        }

        /*
         * We are in melee range. Stop advancing so we do not walk
         * through the target or continually circle around it.
         */
        DAI_InputState
                .movement()
                .setMovement(
                        0.0F,
                        0.0F
                );

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

        DAI_Core.LOGGER.debug(
                "<DAI>: Attacked combat target '{}'.",
                combatTarget.getName().getString()
        );
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

        DAI_Core.LOGGER.debug(
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

        /*
         * Combat requests look through DAI_InputState only.
         *
         * DAI_LookController is the single component responsible for
         * applying requested yaw/pitch to the player. Writing directly here
         * created a second camera-owner path and made controller ordering
         * harder to reason about.
         */
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

        combatActive =
                false;

        attackCooldown =
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

        DAI_Core.LOGGER.debug(
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

        combatActive =
                false;

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
}