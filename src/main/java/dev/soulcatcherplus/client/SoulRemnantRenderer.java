// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.entity.SoulRemnant;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

public final class SoulRemnantRenderer extends EntityRenderer<SoulRemnant,SoulRemnantRenderer.State> {
    public static final class State extends EntityRenderState { Identifier texture; }
    public SoulRemnantRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(SoulRemnant entity,State state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.texture=Identifier.fromNamespaceAndPath("soulcatcherplus","textures/entity/soul_remnant/"+entity.appearance().id()+".png");
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera) {
        pose.pushPose();pose.translate(0,.4+Math.sin(state.ageInTicks*.08)*.08,0);pose.rotate(camera.orientation);
        pose.rotate(com.mojang.math.Axis.YP.rotationDegrees(180));
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(state.texture),(frame,buffer)->{
            float[][] vertices={{-.4f,-.4f,0,1},{.4f,-.4f,1,1},{.4f,.4f,1,0},{-.4f,.4f,0,0}};
            for(var v:vertices) buffer.addVertex(frame.pose(),v[0],v[1],0).setColor(-1).setUv(v[2],v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(frame,0,0,1);
        });
        pose.popPose();super.submit(state,pose,nodes,camera);
    }
}
