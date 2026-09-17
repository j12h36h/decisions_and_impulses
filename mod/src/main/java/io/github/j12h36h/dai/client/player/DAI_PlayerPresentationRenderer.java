package io.github.j12h36h.dai.client.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.j12h36h.dai.client.entity.mesh.DAI_MeshModel;
import io.github.j12h36h.dai.client.entity.mesh.DAI_MeshModelLibrary;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Replaces vanilla player/first-person hand rendering when an experience opts in. */
public final class DAI_PlayerPresentationRenderer {

    private DAI_PlayerPresentationRenderer() {}

    public static void onRenderPlayer(RenderPlayerEvent.Pre<?> event) {
        DAI_PlayerPresentationContext.Context context =
                DAI_PlayerPresentationContext.get(event.getRenderState());
        if (context == null || context.profile().isBlank()) return;

        DAI_PlayerPresentationLibrary.Profile profile = profile(context.profile());
        if (profile == null || profile.model().isBlank()) return;

        DAI_MeshModel model = DAI_MeshModelLibrary.get(profile.model(), profile.id().getNamespace());
        if (model == null || model.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-context.bodyYaw()));
        applyTransform(poseStack, profile.thirdPerson());
        submitModel(
                model,
                profile.texture(),
                poseStack,
                event.getSubmitNodeCollector(),
                event.getRenderState().lightCoords
        );
        poseStack.popPose();
        event.setCanceled(true);
    }

    public static void onRenderHand(RenderHandEvent event) {
        String selected = DAI_PlayerPresentationContext.localProfile();
        if (selected.isBlank()) return;
        DAI_PlayerPresentationLibrary.Profile profile = profile(selected);
        if (profile == null) return;
        DAI_PlayerPresentationLibrary.FirstPerson first = profile.firstPerson();
        if (!first.enabled() || first.model().isBlank() || !matchesHand(first.hand(), event.getHand())) return;

        DAI_MeshModel model = DAI_MeshModelLibrary.get(first.model(), profile.id().getNamespace());
        if (model == null || model.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        applyTransform(poseStack, first.transform());
        submitModel(model, first.texture(), poseStack, event.getSubmitNodeCollector(), event.getPackedLight());
        poseStack.popPose();
        event.setCanceled(true);
    }

    private static DAI_PlayerPresentationLibrary.Profile profile(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String namespace = "decisions_and_impulses";
        int colon = reference.indexOf(':');
        if (colon > 0) namespace = reference.substring(0, colon);
        return DAI_PlayerPresentationLibrary.get(reference, namespace);
    }

    private static boolean matchesHand(String authored, InteractionHand hand) {
        String value = authored == null ? "main" : authored.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "both", "any" -> true;
            case "off", "offhand", "off_hand" -> hand == InteractionHand.OFF_HAND;
            default -> hand == InteractionHand.MAIN_HAND;
        };
    }

    private static void applyTransform(PoseStack poseStack, DAI_PlayerPresentationLibrary.Transform transform) {
        if (transform == null) return;
        poseStack.translate(transform.x(), transform.y(), transform.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(transform.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(transform.yaw()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.roll()));
        poseStack.scale(transform.scale(), transform.scale(), transform.scale());
    }

    private static void submitModel(
            DAI_MeshModel model,
            Identifier textureOverride,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords
    ) {
        for (DAI_MeshModel.Section section : model.sections()) {
            if (section.triangles().isEmpty()) continue;
            int light = section.fullBright() ? LightCoordsUtil.FULL_BRIGHT : lightCoords;
            RenderType renderType = renderType(section, textureOverride);
            collector.submitCustomGeometry(
                    poseStack,
                    renderType,
                    (pose, buffer) -> submitSection(section, pose, buffer, light)
            );
        }
    }

    private static RenderType renderType(DAI_MeshModel.Section section, Identifier override) {
        if (override == null) return section.renderType();
        return switch (section.renderMode()) {
            case SOLID -> RenderTypes.entitySolid(override);
            case TRANSLUCENT -> RenderTypes.entityTranslucent(override);
            case EMISSIVE -> RenderTypes.entityTranslucentEmissive(override);
            case CUTOUT -> RenderTypes.entityCutout(override);
        };
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
