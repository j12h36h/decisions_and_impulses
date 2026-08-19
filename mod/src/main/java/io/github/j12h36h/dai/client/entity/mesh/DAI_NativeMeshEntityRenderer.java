package io.github.j12h36h.dai.client.entity.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;

/**
 * Generic renderer for physical dai:native entities.
 *
 * If the entity's model reference does not resolve to a loaded DAI mesh, this
 * renderer deliberately submits nothing. That preserves compatibility with
 * existing packs that present native entities through item_display rigs.
 */
public final class DAI_NativeMeshEntityRenderer
        extends EntityRenderer<Entity, DAI_MeshEntityRenderState> {

    private final String entityId;
    private final String modelReference;
    private final String fallbackNamespace;

    public DAI_NativeMeshEntityRenderer(
            EntityRendererProvider.Context context,
            String entityId,
            String modelReference
    ) {
        super(context);
        this.entityId = entityId == null ? "" : entityId;
        this.modelReference = modelReference == null ? "" : modelReference;

        int colon = this.entityId.indexOf(':');
        this.fallbackNamespace = colon > 0
                ? this.entityId.substring(0, colon)
                : "decisions_and_impulses";
    }

    @Override
    public DAI_MeshEntityRenderState createRenderState() {
        return new DAI_MeshEntityRenderState();
    }

    @Override
    public void extractRenderState(
            Entity entity,
            DAI_MeshEntityRenderState state,
            float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.yRot = entity.getYRot();
    }

    @Override
    public void submit(
            DAI_MeshEntityRenderState renderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        DAI_MeshModel model = DAI_MeshModelLibrary.get(modelReference, fallbackNamespace);
        if (model == null || model.isEmpty()) {
            // Backwards-compatible invisible/native presentation path.
            return;
        }

        poseStack.pushPose();
        // DAI mesh convention: +Z is model-forward and yaw 0 in Minecraft
        // points toward +Z, so only the entity yaw needs to be inverted.
        poseStack.mulPose(Axis.YP.rotationDegrees(-renderState.yRot));

        for (DAI_MeshModel.Section section : model.sections()) {
            if (section.triangles().isEmpty()) continue;
            final int lightCoords = section.fullBright()
                    ? LightCoordsUtil.FULL_BRIGHT
                    : renderState.lightCoords;

            collector.submitCustomGeometry(
                    poseStack,
                    section.renderType(),
                    (pose, buffer) -> submitSection(section, pose, buffer, lightCoords)
            );
        }

        poseStack.popPose();
    }

    private static void submitSection(
            DAI_MeshModel.Section section,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int lightCoords
    ) {
        for (DAI_MeshModel.Triangle triangle : section.triangles()) {
            submitVertex(buffer, pose, triangle.a(), lightCoords);
            submitVertex(buffer, pose, triangle.b(), lightCoords);
            submitVertex(buffer, pose, triangle.c(), lightCoords);

            // Standard entity render types use QUADS. Repeating the final
            // triangle vertex creates a degenerate fourth corner, allowing
            // arbitrary triangle meshes without a custom GPU pipeline.
            submitVertex(buffer, pose, triangle.c(), lightCoords);
        }
    }

    private static void submitVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            DAI_MeshModel.Vertex vertex,
            int lightCoords
    ) {
        buffer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(255, 255, 255, 255)
                .setUv(vertex.u(), vertex.v())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, vertex.normalX(), vertex.normalY(), vertex.normalZ());
    }
}
