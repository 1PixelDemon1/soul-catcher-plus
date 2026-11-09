// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Mob;
import net.minecraft.util.Mth;

/** Submit the authored Y-down model transform while leaving shadows and labels native. */
abstract class SoulModelRenderer<T extends Mob> extends EntityRenderer<T,SoulModelState> {
    SoulModelRenderer(EntityRendererProvider.Context context,float shadow) { super(context);shadowRadius=shadow; }
    @Override public SoulModelState createRenderState() { return new SoulModelState(); }
    @Override public void extractRenderState(T entity,SoulModelState state,float partial) {
        super.extractRenderState(entity,state,partial);
        state.bodyYaw=Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
        state.headYaw=Mth.rotLerp(partial,entity.yHeadRotO,entity.yHeadRot)-state.bodyYaw;
        state.pitch=Mth.lerp(partial,entity.xRotO,entity.getXRot());
        state.swing=entity.walkAnimation.position(partial);state.amount=entity.walkAnimation.speed(partial);
        state.overlay=OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0?3:10);
    }
    @Override public void submit(SoulModelState state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera) {
        if(!state.isInvisible) {
            pose.pushPose();pose.rotate(Axis.YP.rotationDegrees(180-state.bodyYaw));
            pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
            submitModel(state,pose,nodes);pose.popPose();
        }
        super.submit(state,pose,nodes,camera);
    }
    protected abstract void submitModel(SoulModelState state,PoseStack pose,SubmitNodeCollector nodes);
    static PoseStack poseFrom(PoseStack.Pose origin) {
        var pose=new PoseStack();pose.last().pose().set(origin.pose());pose.last().normal().set(origin.normal());return pose;
    }
}
