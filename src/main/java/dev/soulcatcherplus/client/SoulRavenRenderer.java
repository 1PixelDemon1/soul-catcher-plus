// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulRavenModel;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

public final class SoulRavenRenderer extends SoulModelRenderer<SoulRavenEntity> {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("soulcatcherplus","textures/entity/soul_raven.png");
    private static final Identifier EYES=Identifier.fromNamespaceAndPath("soulcatcherplus","textures/entity/soul_raven_eyes.png");
    private final SoulRavenModel model=new SoulRavenModel();
    public SoulRavenRenderer(EntityRendererProvider.Context context) { super(context,.13f); }
    @Override public void extractRenderState(SoulRavenEntity entity,SoulModelState state,float partial) {
        super.extractRenderState(entity,state,partial);state.flight=entity.getFlightBlend(partial);
        if(entity.isPerched() && entity.perchOwner()!=null) {
            var shoulder=SoulRavenEntity.shoulderPosition(entity.perchOwner(),partial);
            state.x=shoulder.x;state.y=shoulder.y;state.z=shoulder.z;
        }
    }
    @Override public boolean shouldRender(SoulRavenEntity raven,net.minecraft.client.renderer.culling.Frustum clipping,
            double x,double y,double z,float partial) {
        var client=net.minecraft.client.Minecraft.getInstance();
        if(raven.isPerched() && raven.perchOwner()==client.player && client.options.getCameraType().isFirstPerson()) return false;
        return super.shouldRender(raven,clipping,x,y,z,partial);
    }
    @Override protected void submitModel(SoulModelState state,PoseStack pose,SubmitNodeCollector nodes) {
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(TEXTURE),(frame,buffer)->{
            model.setupAnim(state.flight,state.ageInTicks,state.headYaw,state.pitch);
            model.renderToBuffer(poseFrom(frame),buffer,state.lightCoords,state.overlay,1,1,1,1);
        });
        nodes.submitCustomGeometry(pose,RenderTypes.eyes(EYES),(frame,buffer)->{
            model.setupAnim(state.flight,state.ageInTicks,state.headYaw,state.pitch);
            model.renderToBuffer(poseFrom(frame),buffer,LightCoordsUtil.FULL_BRIGHT,state.overlay,1,1,1,1);
        });
    }
}
