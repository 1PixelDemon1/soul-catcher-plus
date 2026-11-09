// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import dev.soulcatcherplus.client.SoulRenderState;
import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderer.class)
public abstract class SoulRenderExtractionMixin {
    @Inject(method="createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",at=@At("RETURN"))
    private void soulcatcherplus$capture(Entity entity,float partial,CallbackInfoReturnable<EntityRenderState> callback) {
        var state=callback.getReturnValue();
        boolean soul=CompanionBrain.isSoul(entity);
        ((SoulRenderState)state).soulcatcherplus$capture(entity.getUUID(),soul);
        if(soul) state.nameTag=null;
    }
}
