// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulCourierModel;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;

public final class SoulCourierRenderer extends SoulModelRenderer<SoulCourierEntity> {
    private static final SoulCourierModel.Material[] MATERIALS=SoulCourierModel.Material.values();
    private final SoulCourierModel model=new SoulCourierModel();
    public SoulCourierRenderer(EntityRendererProvider.Context context) { super(context,.3f); }
    @Override public void extractRenderState(SoulCourierEntity entity,SoulModelState state,float partial) {
        super.extractRenderState(entity,state,partial);state.lid=entity.lidProgress(partial);
    }
    @Override protected void submitModel(SoulModelState state,PoseStack pose,SubmitNodeCollector nodes) {
        for(var material:MATERIALS) nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(material.texture),(frame,buffer)->{
            model.setupAnim(state.lid,state.swing,state.amount,state.headYaw,state.pitch);
            model.render(material,poseFrom(frame),buffer,material==SoulCourierModel.Material.SOUL?LightCoordsUtil.FULL_BRIGHT:state.lightCoords,state.overlay,1,1,1,1);
        });
    }
}
