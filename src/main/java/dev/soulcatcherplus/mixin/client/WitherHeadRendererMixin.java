// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.WitherHeadInterpolation;
import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Capture interpolated values now; queued rendering never reads the live mob. */
@Mixin(value = WitherBossRenderer.class, remap = false)
public abstract class WitherHeadRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/wither/WitherBoss;Lnet/minecraft/client/renderer/entity/state/WitherRenderState;F)V",
        at = @At("TAIL"), remap = false)
    private void soulcatcherplus$smoothHeads(WitherBoss wither, WitherRenderState state,
            float partialTick, CallbackInfo callback) {
        if (!CompanionBrain.isSoul(wither)) return;
        WitherHeadInterpolation heads = (WitherHeadInterpolation)wither;
        for (int head = 0; head < state.yHeadRots.length; head++) {
            state.yHeadRots[head] = heads.soulcatcherplus$headYaw(head, partialTick);
            state.xHeadRots[head] = heads.soulcatcherplus$headPitch(head, partialTick);
        }
    }
}
