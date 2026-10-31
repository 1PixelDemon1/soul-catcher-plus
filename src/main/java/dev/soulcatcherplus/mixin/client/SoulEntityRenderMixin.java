// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.client.SoulVisuals;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wraps the native renderer once, including renderers outside LivingEntityRenderer. */
@Mixin(value = EntityRendererManager.class, remap = false)
public abstract class SoulEntityRenderMixin {
    @Inject(method = {"render", "func_229084_a_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$hideDepartingSoul(Entity entity, double x, double y, double z,
            float yaw, float partialTick, MatrixStack pose, IRenderTypeBuffer buffers, int light,
            CallbackInfo callback) {
        if (SoulVisuals.hideDepartingSoul(entity)) callback.cancel();
    }

    // Official development and verified Forge 1.16.5 SRG names. Exactly one must match.
    // Only the model-render call is wrapped; dispatcher fire, shadows and hitboxes keep their source.
    @Group(name = "soulcatcherplus$modelBuffers", min = 1, max = 1)
    @ModifyArg(method = {"render", "func_229084_a_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V"),
        index = 4, remap = false, require = 0)
    private IRenderTypeBuffer soulcatcherplus$officialBuffers(Entity entity, float yaw, float partialTick,
            MatrixStack pose, IRenderTypeBuffer buffers, int light) {
        return SoulVisuals.companionBuffers(entity, buffers);
    }

    @Group(name = "soulcatcherplus$modelBuffers", min = 1, max = 1)
    @ModifyArg(method = {"render", "func_229084_a_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;func_225623_a_(Lnet/minecraft/entity/Entity;FFLcom/mojang/blaze3d/matrix/MatrixStack;Lnet/minecraft/client/renderer/IRenderTypeBuffer;I)V"),
        index = 4, remap = false, require = 0)
    private IRenderTypeBuffer soulcatcherplus$srgBuffers(Entity entity, float yaw, float partialTick,
            MatrixStack pose, IRenderTypeBuffer buffers, int light) {
        return SoulVisuals.companionBuffers(entity, buffers);
    }
}
