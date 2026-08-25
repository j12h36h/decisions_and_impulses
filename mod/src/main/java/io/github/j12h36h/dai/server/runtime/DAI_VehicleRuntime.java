package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.customization.DAI_GameCustomizationDefinition;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationKind;
import io.github.j12h36h.dai.customization.DAI_GameCustomizationRegistry;
import io.github.j12h36h.dai.logics.core.DAI_Core;
import io.github.j12h36h.dai.network.DAI_VehicleInputPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative controller for dai_vehicles.
 *
 * Existing spawn/mount/dismount actions remain valid; when the player is
 * riding an entity matching a vehicle definition's carrier, the authored
 * physics below take ownership of horizontal movement.
 */
public final class DAI_VehicleRuntime {

    public record Input(
            float forward, float strafe,
            boolean jump, boolean sneak, boolean sprint,
            float yaw, float pitch
    ) {}

    private static final Map<UUID, Input> INPUTS = new ConcurrentHashMap<>();
    private static boolean initialized;

    private DAI_VehicleRuntime() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.register(DAI_VehicleRuntime.class);
        DAI_Core.LOGGER.info("<DAI>: Native vehicle controller initialized.");
    }

    public static void accept(ServerPlayer player, DAI_VehicleInputPayload payload) {
        if (player == null || payload == null) return;
        INPUTS.put(player.getUUID(), new Input(
                Mth.clamp(payload.forward(), -1.0F, 1.0F),
                Mth.clamp(payload.strafe(), -1.0F, 1.0F),
                payload.jump(), payload.sneak(), payload.sprint(),
                payload.yaw(), payload.pitch()
        ));
    }

    public static Input inputFor(ServerPlayer player) {
        if (player == null) return new Input(0, 0, false, false, false, 0, 0);
        return INPUTS.getOrDefault(player.getUUID(),
                new Input(0, 0, false, false, false, player.getYRot(), player.getXRot()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
        if (entityId == null) return;

        DAI_GameCustomizationRegistry.Entry match = null;
        for (var entry : DAI_GameCustomizationRegistry.entries(DAI_GameCustomizationKind.VEHICLE).values()) {
            String carrier = entry.definition().carrier();
            if (!carrier.isBlank() && carrier.equals(entityId.toString())) {
                match = entry;
                break;
            }
        }
        if (match == null) return;

        Input input = INPUTS.getOrDefault(player.getUUID(),
                new Input(0, 0, false, false, false, player.getYRot(), player.getXRot()));
        apply(vehicle, player, match.definition(), input);
    }

    private static void apply(
            Entity vehicle,
            ServerPlayer driver,
            DAI_GameCustomizationDefinition def,
            Input input
    ) {
        double acceleration = positive(def.number("acceleration", 0.045D), 0.045D);
        double reverseAcceleration = positive(def.number("reverse_acceleration", acceleration), acceleration);
        double maxSpeed = positive(def.number("max_speed", 0.8D), 0.8D);
        double reverseSpeed = positive(def.number("reverse_speed", maxSpeed * 0.4D), maxSpeed * 0.4D);
        double drag = Mth.clamp(def.number("drag", 0.06D), 0.0D, 1.0D);
        double braking = Mth.clamp(def.number("braking", 0.14D), 0.0D, 1.0D);
        double turnRate = positive(def.number("turn_rate", 6.0D), 6.0D);
        double strafeFactor = Math.max(0.0D, def.number("strafe_factor", 0.0D));
        double jumpVelocity = Math.max(0.0D, def.number("jump_velocity", 0.42D));
        double boostMultiplier = Math.max(1.0D, def.number("boost_multiplier", 1.35D));
        boolean cameraSteering = def.flag("camera_steering", true);
        boolean allowReverse = def.flag("allow_reverse", true);
        boolean allowJump = def.flag("allow_jump", false);
        boolean gravity = def.flag("gravity", true);

        float targetYaw = cameraSteering ? input.yaw() : vehicle.getYRot() - input.strafe() * (float) turnRate;
        float delta = Mth.wrapDegrees(targetYaw - vehicle.getYRot());
        vehicle.setYRot(vehicle.getYRot() + Mth.clamp(delta, (float)-turnRate, (float)turnRate));
        vehicle.setYHeadRot(vehicle.getYRot());

        Vec3 velocity = vehicle.getDeltaMovement();
        double currentY = gravity ? velocity.y : 0.0D;
        double forward = input.forward();
        if (!allowReverse) forward = Math.max(0.0D, forward);

        double accel = forward < 0.0D ? reverseAcceleration : acceleration;
        float radians = vehicle.getYRot() * ((float)Math.PI / 180F);
        Vec3 forwardVec = new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
        Vec3 rightVec = new Vec3(forwardVec.z, 0.0D, -forwardVec.x);
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);

        if (Math.abs(forward) > 0.001D) {
            horizontal = horizontal.add(forwardVec.scale(accel * forward));
        } else {
            horizontal = horizontal.scale(1.0D - braking);
        }
        if (Math.abs(input.strafe()) > 0.001D && strafeFactor > 0.0D) {
            horizontal = horizontal.add(rightVec.scale(acceleration * strafeFactor * input.strafe()));
        }
        horizontal = horizontal.scale(1.0D - drag);

        double speedCap = forward < 0.0D ? reverseSpeed : maxSpeed;
        if (input.sprint()) speedCap *= boostMultiplier;
        double speed = horizontal.length();
        if (speed > speedCap && speed > 0.0001D) {
            horizontal = horizontal.scale(speedCap / speed);
        }

        if (allowJump && input.jump() && vehicle.onGround()) currentY = jumpVelocity;
        vehicle.setNoGravity(!gravity);
        vehicle.setDeltaMovement(horizontal.x, currentY, horizontal.z);

        driver.setYRot(input.yaw());
        if (def.flag("lock_driver_pitch", false)) driver.setXRot(0.0F);

        String tick = def.event("tick");
        if (!tick.isBlank()) DAI_RuntimeDispatch.dispatch(driver, tick);
        if (input.sprint()) {
            String boost = def.event("boost");
            if (!boost.isBlank()) DAI_RuntimeDispatch.dispatch(driver, boost);
        }
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }
}
