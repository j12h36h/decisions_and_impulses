package io.github.j12h36h.dai.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Renderer for physical native DAI entities whose presentation is supplied by
 * datapack/resource-pack systems (for example articulated item_display rigs).
 * It deliberately submits no vanilla model, texture, shadow, leash or name tag.
 */
public final class DAI_InvisibleEntityRenderer extends EntityRenderer<Entity, EntityRenderState> {

    public DAI_InvisibleEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void extractRenderState(Entity entity, EntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    @Override
    public void submit(
            EntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        // Intentionally empty: presentation is owned by DAI JSON/resource data.
    }
}
