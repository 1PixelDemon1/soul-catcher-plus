// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulcatcherplus.entity.SoulBoltEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

final class SoulBoltRenderer extends EntityRenderer<SoulBoltEntity, SoulBoltRenderer.State> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("soulcatcherplus", "textures/entity/soul_bolt.png");

    SoulBoltRenderer(EntityRendererProvider.Context context) { super(context); }

    static final class State extends EntityRenderState { float yaw, pitch; }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(SoulBoltEntity bolt, State state, float partial) {
        super.extractRenderState(bolt, state, partial);
        state.yaw = Mth.rotLerp(partial, bolt.yRotO, bolt.getYRot());
        state.pitch = Mth.lerp(partial, bolt.xRotO, bolt.getXRot());
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0, .1, 0);
        pose.rotate(Axis.YP.rotationDegrees(state.yaw - 90));
        pose.rotate(Axis.ZP.rotationDegrees(state.pitch));
        pose.rotate(Axis.XP.rotationDegrees(45));
        for (int pass = 0; pass < 2; pass++) {
            nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (frame, buffer) -> {
                for (int i = 0; i < 4; i++) {
                    float u = i == 0 || i == 3 ? 0 : 1;
                    float v = i < 2 ? 1 : 0;
                    buffer.addVertex(frame.pose(), -.68f + .86f * u, .21f - .42f * v, 0)
                        .setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(frame, 0, 0, 1);
                }
            });
            pose.rotate(Axis.XP.rotationDegrees(90));
        }
        pose.popPose();
    }
}
