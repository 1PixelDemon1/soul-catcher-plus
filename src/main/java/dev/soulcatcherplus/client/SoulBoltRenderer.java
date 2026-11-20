// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.math.Vector3f;

public final class SoulBoltRenderer extends EntityRenderer<SoulBoltEntity> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_bolt.png");

    public SoulBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBoltEntity bolt) {
        return TEXTURE;
    }

    @Override
    public void render(SoulBoltEntity bolt, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, bolt.getBbHeight() * 0.5, 0);
        float flightYaw = Mth.rotLerp(partial, bolt.yRotO, bolt.getYRot());
        float flightPitch = Mth.lerp(partial, bolt.xRotO, bolt.getXRot());
        pose.mulPose(Vector3f.YP.rotationDegrees(flightYaw - 90));
        pose.mulPose(Vector3f.ZP.rotationDegrees(flightPitch));
        pose.mulPose(Vector3f.XP.rotationDegrees(45));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        drawStripe(vertices, pose.last());
        pose.mulPose(Vector3f.XP.rotationDegrees(90));
        drawStripe(vertices, pose.last());
        pose.popPose();
        super.render(bolt, yaw, partial, pose, buffers, light);
    }

    private static void drawStripe(VertexConsumer vertices, PoseStack.Pose frame) {
        // Place the bright tip near the collision point, with most of the sprite behind it.
        vertex(vertices, frame, -0.68f, -0.21f, 0, 1);
        vertex(vertices, frame, 0.18f, -0.21f, 1, 1);
        vertex(vertices, frame, 0.18f, 0.21f, 1, 0);
        vertex(vertices, frame, -0.68f, 0.21f, 0, 0);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose frame, float x, float y, float u, float v) {
        vertices.vertex(frame.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.pack(15, 15))
            .normal(frame.normal(), 0, 0, 1).endVertex();
    }
}
