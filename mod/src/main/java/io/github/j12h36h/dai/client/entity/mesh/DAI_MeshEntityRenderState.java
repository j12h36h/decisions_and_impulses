package io.github.j12h36h.dai.client.entity.mesh;

import io.github.j12h36h.dai.client.animations.DAI_AnimationRuntime;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Render-state values needed by DAI's generic native mesh renderer. */
public final class DAI_MeshEntityRenderState extends EntityRenderState {
    public float yRot;
    public float xRot;
    public boolean projectile;
    public boolean mountedVehicle;
    public boolean pitchAroundPivot;
    public double pitchPivotX;
    public double pitchPivotY;
    public double pitchPivotZ;
    public DAI_AnimationRuntime.Transform animation = DAI_AnimationRuntime.Transform.IDENTITY;
}
