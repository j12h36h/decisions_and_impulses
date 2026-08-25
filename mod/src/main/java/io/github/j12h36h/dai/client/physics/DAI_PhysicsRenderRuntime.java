package io.github.j12h36h.dai.client.physics;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.j12h36h.dai.client.creator.DAI_CreatorRuntime;
import io.github.j12h36h.dai.physics.DAI_PhysicsProfile;
import io.github.j12h36h.dai.server.runtime.DAI_PhysicsRuntime;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rotates living-entity visuals to their authored gravity-up axis. */
public final class DAI_PhysicsRenderRuntime {
    public static final ContextKey<Vec3> GRAVITY_UP = new ContextKey<>(
            Identifier.fromNamespaceAndPath("decisions_and_impulses", "gravity_up")
    );
    public static final ContextKey<Boolean> ALIGN_ENTITY = new ContextKey<>(
            Identifier.fromNamespaceAndPath("decisions_and_impulses", "gravity_align_entity")
    );

    private static final Map<UUID, Vec3> CURRENT = new HashMap<>();

    private DAI_PhysicsRenderRuntime() {}

    public static void initialize(IEventBus modBus) {
        modBus.addListener(DAI_PhysicsRenderRuntime::registerModifiers);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, DAI_PhysicsRenderRuntime::onLivingRender);
    }

    private static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                (entity, state) -> applyState(entity, state)
        );
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                applyState(avatar, state);
            }
        });
    }

    private static void applyState(LivingEntity entity, LivingEntityRenderState state) {
        DAI_PhysicsProfile profile = DAI_CreatorRuntime.testPhysics(entity);
        if (profile == null) profile = DAI_PhysicsProfile.activeFor(entity);
        if (profile == null || !profile.alignEntity()) {
            CURRENT.remove(entity.getUUID());
            state.setRenderData(GRAVITY_UP, null);
            state.setRenderData(ALIGN_ENTITY, null);
            return;
        }
        Vec3 previous = CURRENT.getOrDefault(entity.getUUID(), new Vec3(0, -1, 0));
        Vec3 gravity = DAI_PhysicsRuntime.smoothDirection(previous, profile.gravity(), 1.0D / Math.max(1, profile.transitionTicks()));
        CURRENT.put(entity.getUUID(), gravity);
        state.setRenderData(GRAVITY_UP, gravity.scale(-1.0D));
        state.setRenderData(ALIGN_ENTITY, Boolean.TRUE);
    }

    private static void onLivingRender(RenderLivingEvent.Pre<?, ?, ?> event) {
        Boolean align = event.getRenderState().getRenderData(ALIGN_ENTITY);
        Vec3 up = event.getRenderState().getRenderData(GRAVITY_UP);
        if (!Boolean.TRUE.equals(align) || up == null || up.lengthSqr() < 1.0E-8D) return;
        Vec3 normalized = up.normalize();
        if (normalized.distanceToSqr(new Vec3(0, 1, 0)) < 1.0E-7D) return;

        float height = event.getRenderState().boundingBoxHeight;
        PoseStack pose = event.getPoseStack();
        pose.translate(0.0D, height * 0.5D, 0.0D);
        Quaternionf rotation = new Quaternionf().rotationTo(
                0.0F, 1.0F, 0.0F,
                (float)normalized.x, (float)normalized.y, (float)normalized.z
        );
        pose.mulPose(rotation);
        pose.translate(0.0D, -height * 0.5D, 0.0D);
    }
}
