package io.github.j12h36h.dai.client.physics;

import io.github.j12h36h.dai.client.creator.DAI_CreatorRuntime;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import io.github.j12h36h.dai.server.runtime.DAI_PhysicsRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client prediction + smooth camera orientation for arbitrary DAI gravity. */
public final class DAI_ClientPhysicsRuntime {
    private static boolean ownedNoGravity;
    private static boolean originalNoGravity;
    private static boolean lastJump;
    private static float roll;
    private static float pitchOffset;
    private static float yawOffset;
    private static DAI_PhysicsProfile active;
    private static Vec3 currentGravity = new Vec3(0, -1, 0);

    private DAI_ClientPhysicsRuntime() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { reset(); return; }
        DAI_PhysicsProfile profile = DAI_CreatorRuntime.testPhysics(mc.player);
        if (profile == null) profile = DAI_PhysicsProfile.activeFor(mc.player);
        active = profile;
        if (profile == null) {
            if (ownedNoGravity) mc.player.setNoGravity(originalNoGravity);
            ownedNoGravity = false;
            lastJump = false;
            currentGravity = DAI_PhysicsRuntime.smoothDirection(currentGravity, new Vec3(0,-1,0), 0.18D);
            approach(0, 0, 0, 8);
            return;
        }
        if (!ownedNoGravity) {
            originalNoGravity = mc.player.isNoGravity();
            ownedNoGravity = true;
        }

        currentGravity = DAI_PhysicsRuntime.smoothDirection(
                currentGravity,
                profile.gravity(),
                1.0D / Math.max(1, profile.transitionTicks())
        );

        mc.player.setNoGravity(true);
        Vec3 gravity = currentGravity;
        Vec3 velocity = mc.player.getDeltaMovement();
        if (profile.linearDrag() > 0.0D) velocity = velocity.scale(1.0D - profile.linearDrag());
        double along = velocity.dot(gravity);
        if (along < profile.terminalSpeed()) velocity = velocity.add(gravity.scale(profile.gravityStrength()));

        float forward = 0, strafe = 0;
        if (mc.options.keyUp.isDown()) forward += 1;
        if (mc.options.keyDown.isDown()) forward -= 1;
        if (mc.options.keyLeft.isDown()) strafe += 1;
        if (mc.options.keyRight.isDown()) strafe -= 1;

        boolean zeroGravity = profile.zeroGravity();
        Vec3 up = gravity.scale(-1);
        if (zeroGravity && profile.freeFlight()) {
            Vec3 look = mc.player.getLookAngle();
            Vec3 referenceUp = Math.abs(look.y) > 0.98D ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            Vec3 right = look.cross(referenceUp);
            if (right.lengthSqr() < 1.0E-8D) right = new Vec3(-1, 0, 0);
            else right = right.normalize();
            Vec3 cameraUp = right.cross(look);
            if (cameraUp.lengthSqr() < 1.0E-8D) cameraUp = new Vec3(0, 1, 0);
            else cameraUp = cameraUp.normalize();
            double vertical = (mc.options.keyJump.isDown() ? 1.0D : 0.0D)
                    - (mc.options.keyShift.isDown() ? 1.0D : 0.0D);
            Vec3 drive = look.scale(forward).add(right.scale(strafe)).add(cameraUp.scale(vertical));
            if (drive.lengthSqr() > 1.0D) drive = drive.normalize();
            if (drive.lengthSqr() > 1.0E-8D) {
                double sprint = mc.options.keySprint.isDown() ? 1.6D : 1.0D;
                velocity = velocity.add(drive.scale(profile.movementAcceleration() * profile.movementScale() * sprint));
            }
        } else {
            Vec3 tangentForward = tangentForward(mc.player.getYRot(), gravity);
            Vec3 right = tangentForward.cross(up);
            if (right.lengthSqr() > 1.0E-8D) right = right.normalize();
            Vec3 drive = tangentForward.scale(forward).add(right.scale(strafe));
            if (drive.lengthSqr() > 1.0D) drive = drive.normalize();
            if (drive.lengthSqr() > 1.0E-8D) {
                velocity = velocity.add(drive.scale(profile.movementAcceleration() * profile.movementScale()));
            }
        }

        boolean grounded = !zeroGravity
                && !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(gravity.scale(0.075D)));
        if (grounded && velocity.dot(gravity) > 0.0D) {
            double impact = velocity.dot(gravity);
            velocity = velocity.subtract(gravity.scale(impact));
            if (profile.restitution() > 0.0D && impact > 0.08D) {
                velocity = velocity.subtract(gravity.scale(impact * profile.restitution()));
                grounded = false;
            }
        }
        if (grounded && profile.surfaceDrag() > 0.0D) {
            double normal = velocity.dot(gravity);
            Vec3 tangent = velocity.subtract(gravity.scale(normal)).scale(1.0D - profile.surfaceDrag());
            velocity = tangent.add(gravity.scale(normal));
        }
        boolean jump = mc.options.keyJump.isDown();
        if (!zeroGravity && grounded && jump && !lastJump) velocity = velocity.add(up.scale(profile.jumpVelocity()));
        lastJump = jump;
        if (profile.maxSpeed() > 0.0D && velocity.lengthSqr() > profile.maxSpeed() * profile.maxSpeed()) {
            velocity = velocity.normalize().scale(profile.maxSpeed());
        }
        mc.player.setDeltaMovement(velocity);
        mc.player.setOnGround(grounded);
        if (profile.resetFallDistance()) mc.player.resetFallDistance();

        if (profile.alignCamera()) approach(profile.cameraRoll(), profile.cameraPitchOffset(), profile.cameraYawOffset(), profile.transitionTicks());
        else approach(0, 0, 0, profile.transitionTicks());
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (active == null || !active.alignCamera()) return;
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitchOffset);
        event.setYaw(event.getYaw() + yawOffset);
    }

    /**
     * First-person camera anchor mirrored around the entity center along the
     * active gravity-up axis. This makes ceiling/wall gravity feel like the
     * player's head actually rotated with their body instead of leaving the
     * camera stuck at vanilla +Y eye height.
     */
    public static Vec3 cameraPosition(Entity entity, float partialTick) {
        if (entity == null || active == null || !active.alignCamera()) return null;
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        double vanillaOffset = entity.getEyeY() - center.y;
        Vec3 up = currentGravity.scale(-1.0D);
        return center.add(up.scale(vanillaOffset));
    }

    public static boolean active() { return active != null; }
    public static float roll() { return roll; }
    public static float pitchOffset() { return pitchOffset; }
    public static float yawOffset() { return yawOffset; }
    public static Vec3 gravity() { return currentGravity; }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (ownedNoGravity && mc.player != null) mc.player.setNoGravity(originalNoGravity);
        ownedNoGravity = false; originalNoGravity = false; lastJump = false; active = null;
        currentGravity = new Vec3(0,-1,0);
        roll = 0; pitchOffset = 0; yawOffset = 0;
    }

    private static void approach(float targetRoll, float targetPitch, float targetYaw, int ticks) {
        float factor = Math.max(0.02F, Math.min(1.0F, 1.0F / Math.max(1, ticks)));
        roll += wrap(targetRoll - roll) * factor;
        pitchOffset += (targetPitch - pitchOffset) * factor;
        yawOffset += wrap(targetYaw - yawOffset) * factor;
    }

    private static float wrap(float value) {
        while (value > 180) value -= 360;
        while (value < -180) value += 360;
        return value;
    }

    private static Vec3 tangentForward(float yawDegrees, Vec3 gravity) {
        double radians = Math.toRadians(yawDegrees);
        Vec3 raw = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        Vec3 projected = raw.subtract(gravity.scale(raw.dot(gravity)));
        if (projected.lengthSqr() < 1.0E-8D) {
            Vec3 fallback = Math.abs(gravity.y) < 0.9D ? new Vec3(0,1,0) : new Vec3(0,0,1);
            projected = fallback.subtract(gravity.scale(fallback.dot(gravity)));
        }
        return projected.normalize();
    }
}
