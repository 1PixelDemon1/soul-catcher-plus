// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public final class SoulBoltRenderer extends EntityRenderer<SoulBoltEntity> {
    private static final ResourceLocation TEXTURE =
        new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_bolt.png");

    public SoulBoltRenderer(EntityRendererManager context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulBoltEntity bolt) {
        return TEXTURE;
    }

    @Override
    public void render(SoulBoltEntity bolt, float yaw, float partial, MatrixStack pose, IRenderTypeBuffer buffers, int light) {
        pose.pushPose();
        pose.translate(0, bolt.getBbHeight() * 0.5, 0);
        float flightYaw = MathHelper.rotLerp(partial, bolt.yRotO, bolt.yRot);
        float flightPitch = MathHelper.lerp(partial, bolt.xRotO, bolt.xRot);
        pose.mulPose(Vector3f.YP.rotationDegrees(flightYaw - 90));
        pose.mulPose(Vector3f.ZP.rotationDegrees(flightPitch));
        pose.mulPose(Vector3f.XP.rotationDegrees(45));
        IVertexBuilder vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        drawStripe(vertices, pose.last());
        pose.mulPose(Vector3f.XP.rotationDegrees(90));
        drawStripe(vertices, pose.last());
        pose.popPose();
        super.render(bolt, yaw, partial, pose, buffers, light);
    }

    private static void drawStripe(IVertexBuilder vertices, MatrixStack.Entry frame) {
        // Place the bright tip near the collision point, with most of the sprite behind it.
        vertex(vertices, frame, -0.68f, -0.21f, 0, 1);
        vertex(vertices, frame, 0.18f, -0.21f, 1, 1);
        vertex(vertices, frame, 0.18f, 0.21f, 1, 0);
        vertex(vertices, frame, -0.68f, 0.21f, 0, 0);
    }

    private static void vertex(IVertexBuilder vertices, MatrixStack.Entry frame, float x, float y, float u, float v) {
        vertices.vertex(frame.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.pack(15, 15))
            .normal(frame.normal(), 0, 0, 1).endVertex();
    }
}
