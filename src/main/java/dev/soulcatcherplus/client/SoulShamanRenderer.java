// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulShamanModel;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;

public final class SoulShamanRenderer extends SoulModelRenderer<SoulShamanEntity> {
    private static final SoulShamanModel.Material[] MATERIALS=SoulShamanModel.Material.values();
    private final SoulShamanModel model=new SoulShamanModel();
    public SoulShamanRenderer(EntityRendererProvider.Context context) { super(context,.3f); }
    @Override public void extractRenderState(SoulShamanEntity entity,SoulModelState state,float partial) {
        super.extractRenderState(entity,state,partial);state.castPhase=entity.castPhase(state.ageInTicks);
    }
    @Override protected void submitModel(SoulModelState state,PoseStack pose,SubmitNodeCollector nodes) {
        for(var material:MATERIALS) nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(material.texture),(frame,buffer)->{
            model.setupAnim(state.castPhase,state.ageInTicks,state.headYaw,state.pitch);
            model.render(material,poseFrom(frame),buffer,material.emissive?LightCoordsUtil.FULL_BRIGHT:state.lightCoords,state.overlay,1,1,1,1);
        });
    }
}
