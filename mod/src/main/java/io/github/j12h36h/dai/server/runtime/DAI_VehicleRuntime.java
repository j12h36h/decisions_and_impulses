package io.github.j12h36h.dai.server.runtime;

import io.github.j12h36h.dai.attributes.DAI_NativeAttributeSupport;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative controller for dai_vehicles.
 *
 * DAI 3.3 DirtBikeLife extension:
 * - preserves the existing default vehicle behavior;
 * - adds the opt-in "manual_motorcycle" control profile;
 * - supports LMB throttle, RMB tap/double-tap/hold transmission + braking;
 * - uses W/S as rider lean and A/D as steering;
 * - simulates wheelies, loop-out crashes, a three-second crash echo, and a
 *   deterministic rider-authoritative reset.
 */
public final class DAI_VehicleRuntime {

    public record Input(
            float forward,
            float strafe,
            boolean jump,
            boolean sneak,
            boolean sprint,
            boolean attack,
            boolean use,
            float yaw,
            float pitch
    ) {}

    private static final Map<UUID, Input> INPUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, MotorcycleState> MOTORCYCLE = new ConcurrentHashMap<>();
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
                payload.jump(),
                payload.sneak(),
                payload.sprint(),
                payload.attack(),
                payload.use(),
                payload.yaw(),
                payload.pitch()
        ));
    }

    public static Input inputFor(ServerPlayer player) {
        if (player == null) return new Input(0, 0, false, false, false, false, false, 0, 0);
        return INPUTS.getOrDefault(player.getUUID(),
                new Input(0, 0, false, false, false, false, false, player.getYRot(), player.getXRot()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            tickPlayer(player);
        }
    }

    private static void tickPlayer(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) {
            MOTORCYCLE.remove(player.getUUID());
            return;
        }

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
                new Input(0, 0, false, false, false, false, false, player.getYRot(), player.getXRot()));

        DAI_GameCustomizationDefinition definition = match.definition();
        if ("manual_motorcycle".equalsIgnoreCase(definition.property("control_profile"))) {
            applyManualMotorcycle(vehicle, player, definition, input);
        } else {
            MOTORCYCLE.remove(player.getUUID());
            applyDefault(vehicle, player, definition, input);
        }
    }

    private static void applyDefault(
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
        Vec3 forwardVec = forwardVector(vehicle.getYRot());
        Vec3 rightVec = new Vec3(forwardVec.z, 0.0D, -forwardVec.x);
        Vec3 horizontal = new Vec3(velocity.x, 0, velocity.z);

        if (Math.abs(forward) > 0.001D) horizontal = horizontal.add(forwardVec.scale(accel * forward));
        else horizontal = horizontal.scale(1.0D - braking);

        if (Math.abs(input.strafe()) > 0.001D && strafeFactor > 0.0D) {
            horizontal = horizontal.add(rightVec.scale(acceleration * strafeFactor * input.strafe()));
        }
        horizontal = horizontal.scale(1.0D - drag);

        double speedCap = forward < 0.0D ? reverseSpeed : maxSpeed;
        if (input.sprint()) speedCap *= boostMultiplier;
        double speed = horizontal.length();
        if (speed > speedCap && speed > 0.0001D) horizontal = horizontal.scale(speedCap / speed);

        if (allowJump && input.jump() && vehicle.onGround()) currentY = jumpVelocity;
        vehicle.setNoGravity(!gravity);
        vehicle.setDeltaMovement(horizontal.x, currentY, horizontal.z);

        driver.setYRot(input.yaw());
        if (def.flag("lock_driver_pitch", false)) driver.setXRot(0.0F);

        dispatch(def, driver, "tick");
        if (input.sprint()) dispatch(def, driver, "boost");
    }

    private static void applyManualMotorcycle(
            Entity vehicle,
            ServerPlayer driver,
            DAI_GameCustomizationDefinition def,
            Input input
    ) {
        MotorcycleState state = MOTORCYCLE.computeIfAbsent(driver.getUUID(), key -> MotorcycleState.create(def));

        if (state.vehicleId != null && !state.vehicleId.equals(vehicle.getUUID())) {
            state = MotorcycleState.create(def);
            MOTORCYCLE.put(driver.getUUID(), state);
        }
        state.vehicleId = vehicle.getUUID();

        if (state.crashing) {
            tickCrash(vehicle, driver, def, state);
            return;
        }

        // Sample actual world displacement before issuing this tick's command.
        // Native LivingEntity carriers apply their own movement/friction during
        // entity ticking, so getDeltaMovement alone is not the same thing as
        // the road speed the rider actually experiences.
        boolean hadMotionSample = state.motionSampleInitialized;
        double movedThisTick = hadMotionSample
                ? Math.hypot(vehicle.getX() - state.lastVehicleX, vehicle.getZ() - state.lastVehicleZ)
                : 0.0D;
        state.lastVehicleX = vehicle.getX();
        state.lastVehicleZ = vehicle.getZ();
        state.motionSampleInitialized = true;
        if (state.obstacleLiftCooldown > 0) state.obstacleLiftCooldown--;

        final int gearCount = clampInt((int)Math.round(def.number("gear_count", 5)), 1, 8);
        final int brakeHoldTicks = Math.max(1, (int)Math.round(def.number("brake_hold_ticks", 20)));
        final int doubleTapTicks = Math.max(1, (int)Math.round(def.number("double_tap_ticks", 5)));
        final double softBrakeFactor = Mth.clamp(def.number("soft_brake_factor", 0.5D), 0.0D, 1.0D);

        boolean use = input.use();
        if (use && !state.lastUse) {
            state.useHeldTicks = 0;
            state.fullBrake = false;
        }
        if (use) {
            state.useHeldTicks++;
            if (!state.fullBrake && state.useHeldTicks >= brakeHoldTicks) {
                state.fullBrake = true;
                state.pendingTapTicks = 0;
                dispatch(def, driver, "brake_full");
            }
        }
        if (!use && state.lastUse) {
            if (state.fullBrake) {
                state.pendingTapTicks = 0;
            } else if (state.pendingTapTicks > 0) {
                if (state.gear > 0) {
                    state.gear--;
                    dispatch(def, driver, "shift_down");
                }
                state.pendingTapTicks = 0;
            } else {
                state.pendingTapTicks = doubleTapTicks;
            }
            state.useHeldTicks = 0;
            state.fullBrake = false;
        }
        state.lastUse = use;

        if (!use && state.pendingTapTicks > 0) {
            state.pendingTapTicks--;
            if (state.pendingTapTicks == 0 && state.gear < gearCount) {
                state.gear++;
                dispatch(def, driver, "shift_up");
            }
        }

        double braking = Mth.clamp(def.number("braking", 0.18D), 0.0D, 1.0D);
        double brakeAmount = use ? (state.fullBrake ? 1.0D : softBrakeFactor) : 0.0D;
        boolean attack = input.attack();
        boolean throttleReleased = !attack && state.lastAttack;
        state.lastAttack = attack;
        if (throttleReleased) {
            state.throttleReleaseTicks = Math.max(1,
                    (int)Math.round(def.number("throttle_release_ticks", 4)));
        } else if (state.throttleReleaseTicks > 0) {
            state.throttleReleaseTicks--;
        }
        double throttle = attack ? 1.0D : 0.0D;
        double rearLean = Math.max(0.0D, -input.forward());
        double forwardLean = Math.max(0.0D, input.forward());

        double maxSpeed = positive(def.number("max_speed", 1.05D), 1.05D);
        double coastDrag = Mth.clamp(def.number("drag", 0.015D), 0.0D, 0.8D);
        double engineBraking = Mth.clamp(def.number("engine_braking", 0.018D), 0.0D, 0.5D);
        double lateralGrip = Mth.clamp(def.number("lateral_grip", 0.72D), 0.0D, 1.0D);
        double turnRate = positive(def.number("turn_rate", 4.8D), 4.8D);
        double highSpeedSteer = Mth.clamp(def.number("high_speed_steer_factor", 0.42D), 0.05D, 1.0D);

        Vec3 velocity = vehicle.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        double horizontalSpeed = horizontal.length();
        double speedNorm = Mth.clamp(horizontalSpeed / Math.max(0.001D, maxSpeed), 0.0D, 1.0D);

        double steerScale = Mth.lerp(speedNorm, 1.0D, highSpeedSteer);
        float yawDelta = (float)(-input.strafe() * turnRate * steerScale);
        vehicle.setYRot(vehicle.getYRot() + yawDelta);
        vehicle.setYHeadRot(vehicle.getYRot());
        driver.setYRot(input.yaw());

        Vec3 forwardVec = forwardVector(vehicle.getYRot());
        double signedSpeed = horizontal.dot(forwardVec);
        if (signedSpeed < 0.0D) signedSpeed *= 0.35D;
        Vec3 lateral = horizontal.subtract(forwardVec.scale(signedSpeed)).scale(1.0D - lateralGrip);
        horizontal = forwardVec.scale(signedSpeed).add(lateral);

        double gearCap = gearSpeed(def, state.gear, gearCount, maxSpeed);
        double torque = gearTorque(def, state.gear, gearCount);

        // The native carrier consumes part of the commanded horizontal velocity
        // before world displacement is observed. Compensate for that carrier
        // retention so the authored gear_N_speed values describe actual road
        // speed, not merely the hidden pre-friction velocity command. A small
        // high-speed term accounts for the slightly greater attenuation seen
        // as the carrier moves faster.
        double carrierCompBase = Mth.clamp(def.number("carrier_speed_compensation", 1.42D), 1.0D, 2.0D);
        double carrierCompHigh = Mth.clamp(def.number("high_speed_compensation", 0.10D), 0.0D, 0.50D);
        double gearSpeedNorm = state.gear > 0
                ? Mth.clamp(gearCap / Math.max(0.001D, maxSpeed), 0.0D, 1.0D)
                : 0.0D;
        double carrierComp = carrierCompBase + carrierCompHigh * gearSpeedNorm;
        double commandedGearCap = state.gear > 0 ? gearCap * carrierComp : 0.0D;

        // Manual motorcycles are driven toward a gear-owned road-speed target
        // instead of relying on tiny additive impulses. PathfinderMob applies
        // its own ground friction between server ticks; with the old impulse
        // model that friction overwhelmed the lower torque in higher gears and
        // every gear converged to nearly the same real movement speed. The
        // response model continually restores the selected gear's road speed
        // while still letting lower gears feel more immediate.
        if (state.gear > 0 && throttle > 0.0D) {
            double baseResponse = Mth.clamp(def.number("throttle_response", 0.48D), 0.05D, 0.95D);
            double torqueResponse = Mth.clamp(0.72D + 0.28D * torque, 0.55D, 1.20D);
            double response = Mth.clamp(baseResponse * torqueResponse, 0.05D, 0.95D);
            double targetSpeed = commandedGearCap;

            if (signedSpeed < 0.0D) signedSpeed *= 0.35D;
            signedSpeed = Mth.lerp(response, signedSpeed, targetSpeed);
            horizontal = forwardVec.scale(signedSpeed).add(lateral);
        } else if (state.gear > 0) {
            // LMB release is a drivetrain cut, not a delayed fade of the old
            // powered command. For a few ticks after the falling edge, apply
            // stronger engine braking so the bike stops feeling like it is
            // still being driven after the rider lets go, while preserving
            // enough momentum to coast naturally.
            double releaseBrakeMultiplier = state.throttleReleaseTicks > 0
                    ? Mth.clamp(def.number("throttle_release_engine_brake_multiplier", 2.35D), 1.0D, 6.0D)
                    : 1.0D;
            horizontal = horizontal.scale(1.0D
                    - engineBraking * Math.max(0.25D, torque) * releaseBrakeMultiplier);
            horizontal = horizontal.scale(1.0D - coastDrag);
        } else {
            horizontal = horizontal.scale(1.0D - coastDrag);
        }

        if (brakeAmount > 0.0D) {
            horizontal = horizontal.scale(Math.max(0.0D, 1.0D - braking * brakeAmount));
        }

        double newSpeed = horizontal.length();
        // Manual transmissions own their per-gear road-speed ceiling. The old
        // implementation only stopped adding torque at gearCap and then clamped
        // against the vehicle-wide maxSpeed, so momentum from a higher gear could
        // make every gear appear to have the same top speed. Enforce the selected
        // gear's authored cap directly; neutral remains unpowered and uses the
        // global safety cap only.
        double activeSpeedCap = state.gear > 0 ? commandedGearCap : maxSpeed;
        if (newSpeed > activeSpeedCap && newSpeed > 0.0001D) {
            horizontal = horizontal.scale(activeSpeedCap / newSpeed);
        }

        double idleRpm = positive(def.number("idle_rpm", 1800D), 1800D);
        double redlineRpm = Math.max(idleRpm + 500D, def.number("redline_rpm", 10500D));
        if (state.gear == 0) {
            double neutralTarget = idleRpm + throttle * (redlineRpm - idleRpm) * 0.72D;
            state.rpm += (neutralTarget - state.rpm) * 0.12D;
        } else {
            // RPM follows estimated road speed rather than the compensated
            // carrier command, otherwise friction compensation would make every
            // gear appear to sit at redline too early.
            double estimatedRoadSpeed = hadMotionSample
                    ? movedThisTick
                    : horizontal.length() / Math.max(1.0D, carrierComp);
            double gearFraction = Mth.clamp(estimatedRoadSpeed / Math.max(0.001D, gearCap), 0.0D, 1.15D);
            double targetRpm = idleRpm + gearFraction * (redlineRpm - idleRpm);
            state.rpm += (targetRpm - state.rpm) * 0.22D;
        }
        state.rpm = Mth.clamp(state.rpm, idleRpm, redlineRpm * 1.03D);

        boolean grounded = vehicle.onGround();
        updatePitchPhysics(vehicle, def, state, throttle, brakeAmount, rearLean, forwardLean,
                speedNorm, grounded, throttleReleased);
        updateMotorcycleStepHeight(vehicle, def, state, throttle, grounded);

        double currentY = velocity.y;

        // A visual wheelie does not rotate Minecraft's axis-aligned collision
        // box, so step_height alone can still leave the carrier pinned against
        // a one-block ledge. Detect the specific case where a front-lifted bike
        // was commanded forward but made almost no real world progress, then
        // give it a short suspension/front-wheel lift. This behaves like riding
        // the raised front tire onto the ledge rather than granting permanent
        // auto-jump.
        double wheelieStepMinAngle = Mth.clamp(def.number("wheelie_step_min_angle", 18.0D), 0.0D, 70.0D);
        double obstacleStallRatio = Mth.clamp(def.number("wheelie_obstacle_stall_ratio", 0.34D), 0.05D, 0.80D);
        double obstacleLiftVelocity = Mth.clamp(def.number("wheelie_obstacle_lift_velocity", 0.43D), 0.10D, 0.80D);
        int obstacleLiftCooldownTicks = Math.max(1, (int)Math.round(def.number("wheelie_obstacle_cooldown_ticks", 7)));
        boolean stalledIntoLedge = grounded
                && throttle > 0.0D
                && state.wheelieAngle >= wheelieStepMinAngle
                && hadMotionSample
                && state.lastCommandedSpeed > 0.08D
                && movedThisTick < state.lastCommandedSpeed * obstacleStallRatio
                && state.obstacleLiftCooldown <= 0;
        if (stalledIntoLedge) {
            currentY = Math.max(currentY, obstacleLiftVelocity);
            state.obstacleLiftCooldown = obstacleLiftCooldownTicks;
        }

        vehicle.setNoGravity(!def.flag("gravity", true));
        vehicle.setDeltaMovement(horizontal.x, currentY, horizontal.z);
        state.lastCommandedSpeed = horizontal.length();

        double stableAngle = Math.max(0.0D, def.number("stable_snapshot_angle", 55.0D));
        if (grounded && state.wheelieAngle <= stableAngle) {
            state.snapshot = new RiderSnapshot(
                    driver.getX(), driver.getY(), driver.getZ(),
                    driver.getYRot(), driver.getXRot()
            );
        }

        boolean hardLanding = !state.wasGrounded && grounded
                && state.lastVerticalVelocity < -Math.abs(def.number("hard_landing_velocity", 0.92D));
        double crashAngle = Mth.clamp(def.number("crash_angle", 84.0D), 60.0D, 120.0D);
        if (state.wheelieAngle >= crashAngle || hardLanding) {
            beginCrash(vehicle, driver, def, state);
            return;
        }

        state.wasGrounded = grounded;
        state.lastVerticalVelocity = currentY;

        state.soundTicks++;
        int soundInterval = Math.max(2, (int)Math.round(def.number("engine_sound_interval", 7)));
        if (state.soundTicks >= soundInterval) {
            state.soundTicks = 0;
            double rpmNorm = (state.rpm - idleRpm) / Math.max(1.0D, redlineRpm - idleRpm);
            if (rpmNorm < 0.18D) dispatch(def, driver, "engine_idle");
            else if (rpmNorm < 0.50D) dispatch(def, driver, "engine_low");
            else if (rpmNorm < 0.78D) dispatch(def, driver, "engine_mid");
            else dispatch(def, driver, "engine_high");
        }

        dispatch(def, driver, "tick");
    }


    private static void updateMotorcycleStepHeight(
            Entity vehicle,
            DAI_GameCustomizationDefinition def,
            MotorcycleState state,
            double throttle,
            boolean grounded
    ) {
        if (!(vehicle instanceof LivingEntity living)) return;

        double normalStep = Mth.clamp(def.number("normal_step_height", 0.60D), 0.0D, 1.25D);
        double wheelieStep = Mth.clamp(def.number("wheelie_step_height", 1.15D), normalStep, 1.50D);
        double wheelieMinAngle = Mth.clamp(def.number("wheelie_step_min_angle", 18.0D), 0.0D, 70.0D);

        // A pitched visual mesh does not change Minecraft's axis-aligned entity
        // collision box. Raise the native living-entity step height only while
        // the front end is intentionally lifted and the rider is driving, which
        // lets the rear wheel follow over a one-block ledge without turning the
        // bike into a permanently auto-stepping vehicle.
        boolean frontLifted = grounded && throttle > 0.0D && state.wheelieAngle >= wheelieMinAngle;
        DAI_NativeAttributeSupport.setBase(
                living,
                "minecraft:step_height",
                frontLifted ? wheelieStep : normalStep
        );
    }

    private static void updatePitchPhysics(
            Entity vehicle,
            DAI_GameCustomizationDefinition def,
            MotorcycleState state,
            double throttle,
            double brakeAmount,
            double rearLean,
            double forwardLean,
            double speedNorm,
            boolean grounded,
            boolean throttleReleased
    ) {
        double balance = Mth.clamp(def.number("wheelie_balance_angle", 58.0D), 30.0D, 80.0D);
        double wheeliePower = def.number("wheelie_power", 0.24D);
        double leanPower = def.number("lean_pitch_power", 0.13D);
        double brakePitch = def.number("brake_pitch_power", 0.22D);
        double restore = Math.max(0.0D, def.number("wheelie_restore", 0.0060D));
        double overBalance = Math.max(0.0D, def.number("over_balance_gravity", 0.0080D));
        double airPitch = Math.max(0.0D, def.number("air_pitch_power", 0.075D));

        // Engine torque can leave positive angular momentum in the front end
        // for several ticks after LMB is released. Cut only that upward carry
        // on the release edge; the bike still falls under the normal balance
        // and restore forces rather than snapping flat.
        if (grounded && throttleReleased && state.wheelieVelocity > 0.0D) {
            double releaseDamping = Mth.clamp(
                    def.number("wheelie_throttle_release_damping", 0.52D),
                    0.0D, 1.0D
            );
            state.wheelieVelocity *= releaseDamping;
        }

        double angularAcceleration;
        if (grounded) {
            double lowSpeedLift = 1.0D - 0.70D * speedNorm;
            angularAcceleration = throttle * wheeliePower * lowSpeedLift
                    + rearLean * leanPower
                    - forwardLean * leanPower * 1.15D
                    - brakeAmount * brakePitch;

            if (state.wheelieAngle <= balance) {
                angularAcceleration -= state.wheelieAngle * restore;
            } else {
                angularAcceleration += (state.wheelieAngle - balance) * overBalance;
            }
            state.wheelieVelocity = (state.wheelieVelocity + angularAcceleration) * 0.93D;
        } else {
            angularAcceleration = (rearLean - forwardLean) * airPitch;
            state.wheelieVelocity = (state.wheelieVelocity + angularAcceleration) * 0.992D;
        }

        state.wheelieAngle = Mth.clamp(state.wheelieAngle + state.wheelieVelocity, 0.0D, 115.0D);
        if (state.wheelieAngle <= 0.001D && state.wheelieVelocity < 0.0D) state.wheelieVelocity = 0.0D;
        vehicle.setXRot((float)-state.wheelieAngle);
    }

    private static void beginCrash(
            Entity vehicle,
            ServerPlayer driver,
            DAI_GameCustomizationDefinition def,
            MotorcycleState state
    ) {
        if (state.snapshot == null) {
            state.snapshot = new RiderSnapshot(
                    driver.getX(), driver.getY(), driver.getZ(),
                    driver.getYRot(), driver.getXRot()
            );
        }
        state.crashing = true;
        state.crashTicks = Math.max(1, (int)Math.round(def.number("crash_simulation_ticks", 60)));
        state.pendingTapTicks = 0;
        state.fullBrake = false;
        state.useHeldTicks = 0;
        Vec3 motion = vehicle.getDeltaMovement();
        Vec3 forward = forwardVector(vehicle.getYRot());
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        double sideways = ((driver.getUUID().hashCode() & 1) == 0 ? 1.0D : -1.0D) * 0.12D;
        vehicle.setDeltaMovement(motion.add(right.scale(sideways)).add(0.0D, 0.10D, 0.0D));
        dispatch(def, driver, "crash");
    }

    private static void tickCrash(
            Entity vehicle,
            ServerPlayer driver,
            DAI_GameCustomizationDefinition def,
            MotorcycleState state
    ) {
        state.crashTicks--;
        vehicle.setXRot(vehicle.getXRot() - (float)def.number("crash_pitch_spin", 10.0D));
        vehicle.setYRot(vehicle.getYRot() + (float)def.number("crash_yaw_spin", 4.0D));
        vehicle.setYHeadRot(vehicle.getYRot());
        Vec3 motion = vehicle.getDeltaMovement();
        vehicle.setDeltaMovement(motion.x * 0.965D, motion.y, motion.z * 0.965D);

        if (state.crashTicks > 0) return;
        resetFromEcho(vehicle, driver, def, state);
    }

    private static void resetFromEcho(
            Entity vehicle,
            ServerPlayer driver,
            DAI_GameCustomizationDefinition def,
            MotorcycleState state
    ) {
        RiderSnapshot snap = state.snapshot;
        if (snap == null) return;

        double seatYOffset = def.number("reset_vehicle_y_offset", 0.92D);
        driver.stopRiding();

        String tp = String.format(Locale.ROOT,
                "command:tp @s %.5f %.5f %.5f %.3f %.3f",
                snap.x, snap.y, snap.z, snap.yaw, snap.pitch);
        DAI_RuntimeDispatch.dispatch(driver, tp);

        vehicle.setPos(snap.x, snap.y - seatYOffset, snap.z);
        vehicle.setYRot(snap.yaw);
        vehicle.setYHeadRot(snap.yaw);
        vehicle.setXRot(0.0F);
        vehicle.setDeltaMovement(Vec3.ZERO);
        vehicle.fallDistance = 0.0F;

        driver.setDeltaMovement(Vec3.ZERO);
        driver.fallDistance = 0.0F;
        driver.setYRot(snap.yaw);
        driver.setXRot(snap.pitch);
        driver.startRiding(vehicle);

        state.gear = clampInt((int)Math.round(def.number("reset_gear", 1)), 0,
                clampInt((int)Math.round(def.number("gear_count", 5)), 1, 8));
        state.rpm = positive(def.number("idle_rpm", 1800D), 1800D);
        state.wheelieAngle = 0.0D;
        state.wheelieVelocity = 0.0D;
        state.crashing = false;
        state.crashTicks = 0;
        state.pendingTapTicks = 0;
        state.fullBrake = false;
        state.useHeldTicks = 0;
        state.lastUse = false;
        state.lastAttack = false;
        state.throttleReleaseTicks = 0;
        state.wasGrounded = true;
        state.lastVerticalVelocity = 0.0D;
        state.motionSampleInitialized = false;
        state.lastCommandedSpeed = 0.0D;
        state.obstacleLiftCooldown = 0;
        dispatch(def, driver, "reset");
    }

    private static double gearSpeed(
            DAI_GameCustomizationDefinition def,
            int gear,
            int gearCount,
            double maxSpeed
    ) {
        if (gear <= 0) return 0.0D;
        double authored = def.number("gear_" + gear + "_speed", -1.0D);
        if (authored > 0.0D) return authored;
        return maxSpeed * (0.28D + 0.72D * ((double)gear / (double)gearCount));
    }

    private static double gearTorque(
            DAI_GameCustomizationDefinition def,
            int gear,
            int gearCount
    ) {
        if (gear <= 0) return 0.0D;
        double authored = def.number("gear_" + gear + "_torque", -1.0D);
        if (authored > 0.0D) return authored;
        return 1.15D - 0.60D * ((double)(gear - 1) / Math.max(1.0D, gearCount - 1.0D));
    }

    private static Vec3 forwardVector(float yaw) {
        float radians = yaw * ((float)Math.PI / 180F);
        return new Vec3(-Mth.sin(radians), 0.0D, Mth.cos(radians));
    }

    private static void dispatch(DAI_GameCustomizationDefinition def, ServerPlayer driver, String eventName) {
        String reference = def.event(eventName);
        if (!reference.isBlank()) DAI_RuntimeDispatch.dispatch(driver, reference);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private record RiderSnapshot(
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {}

    private static final class MotorcycleState {
        private UUID vehicleId;
        private int gear;
        private double rpm;
        private double wheelieAngle;
        private double wheelieVelocity;
        private boolean lastUse;
        private boolean lastAttack;
        private int throttleReleaseTicks;
        private int useHeldTicks;
        private boolean fullBrake;
        private int pendingTapTicks;
        private boolean crashing;
        private int crashTicks;
        private int soundTicks;
        private RiderSnapshot snapshot;
        private boolean wasGrounded = true;
        private double lastVerticalVelocity;
        private boolean motionSampleInitialized;
        private double lastVehicleX;
        private double lastVehicleZ;
        private double lastCommandedSpeed;
        private int obstacleLiftCooldown;

        private static MotorcycleState create(DAI_GameCustomizationDefinition def) {
            MotorcycleState state = new MotorcycleState();
            int count = clampInt((int)Math.round(def.number("gear_count", 5)), 1, 8);
            state.gear = clampInt((int)Math.round(def.number("start_gear", 1)), 0, count);
            state.rpm = positive(def.number("idle_rpm", 1800D), 1800D);
            return state;
        }
    }
}
