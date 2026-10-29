// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.SoulVisuals;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wraps the native renderer once, including renderers outside LivingEntityRenderer. */
@Mixin(value = EntityRenderDispatcher.class, remap = false)
public abstract class SoulEntityRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$hideDepartingSoul(Entity entity, double x, double y, double z,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            CallbackInfo callback) {
        if (SoulVisuals.hideDepartingSoul(entity)) callback.cancel();
    }

    // Only the model-render call is wrapped; dispatcher fire, shadows and hitboxes keep their source.
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 4, remap = false)
    private MultiBufferSource soulcatcherplus$officialBuffers(Entity entity, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        return SoulVisuals.companionBuffers(entity, buffers);
    }

}
