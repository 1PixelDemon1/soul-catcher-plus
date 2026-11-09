// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.SoulVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
public abstract class SoulEntityRenderMixin {
    @Inject(method="submit",at=@At("HEAD"),cancellable=true)
    private void soulcatcherplus$hide(EntityRenderState state,CameraRenderState camera,double x,double y,double z,PoseStack pose,SubmitNodeCollector nodes,CallbackInfo callback) {
        if(SoulVisuals.hideDepartingSoul(state)) callback.cancel();
    }
    @ModifyArg(method="submit",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"),index=2)
    private SubmitNodeCollector soulcatcherplus$nodes(EntityRenderState state,PoseStack pose,SubmitNodeCollector nodes,CameraRenderState camera) {
        return SoulVisuals.companionNodes(state,nodes);
    }
}
