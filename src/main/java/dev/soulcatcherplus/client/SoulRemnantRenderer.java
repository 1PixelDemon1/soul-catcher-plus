// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulRemnant;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SoulRemnantRenderer extends EntityRenderer<SoulRemnant> {
    private static final Map<SoulRemnant.Appearance, ResourceLocation> TEXTURES = com.google.common.collect.ImmutableMap.of(
        SoulRemnant.Appearance.SMALL, texture("small"), SoulRemnant.Appearance.MEDIUM, texture("medium"),
        SoulRemnant.Appearance.LARGE, texture("large"), SoulRemnant.Appearance.SKULL, texture("skull"));
    public SoulRemnantRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) { super(context); }
    private static ResourceLocation texture(String name) {
        return new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_remnant/" + name + ".png");
    }
    @Override public ResourceLocation getTextureLocation(SoulRemnant entity) { return TEXTURES.get(entity.appearance()); }
    @Override public void render(SoulRemnant entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.4 + Math.sin((entity.tickCount + partial) * 0.08) * 0.08, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180));
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        PoseStack.Pose frame = pose.last();
        vertex(vertices, frame, -0.4f, -0.4f, 0, 1);
        vertex(vertices, frame, 0.4f, -0.4f, 1, 1);
        vertex(vertices, frame, 0.4f, 0.4f, 1, 0);
        vertex(vertices, frame, -0.4f, 0.4f, 0, 0);
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose frame, float x, float y, float u, float v) {
        vertices.vertex(frame.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
            .normal(frame.normal(), 0, 0, 1).endVertex();
    }
}
