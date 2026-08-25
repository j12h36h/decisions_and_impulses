package io.github.j12h36h.dai.client.mixin;

import io.github.j12h36h.dai.client.physics.DAI_ClientPhysicsRuntime;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-anchors the first-person camera eye position for wall/ceiling gravity. */
@Mixin(Camera.class)
public abstract class Mixin_Camera {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    /**
     * Minecraft 26.2 moved camera setup into Camera#update(DeltaTracker).
     * Apply the gravity-relative eye anchor after vanilla has finished its
     * normal camera placement so the authored gravity frame wins cleanly.
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void dai$gravityCamera(DeltaTracker deltaTracker, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.getCameraType().isFirstPerson() || !DAI_ClientPhysicsRuntime.active()) return;
        Entity entity = minecraft.getCameraEntity();
        if (entity == null) return;
        Vec3 position = DAI_ClientPhysicsRuntime.cameraPosition(entity, 1.0F);
        if (position != null) setPosition(position);
    }
}
