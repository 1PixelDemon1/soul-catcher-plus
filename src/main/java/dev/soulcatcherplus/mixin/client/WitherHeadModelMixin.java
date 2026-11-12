// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.WitherHeadInterpolation;
import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WitherBossModel.class, remap = false)
public abstract class WitherHeadModelMixin {
    @Shadow(aliases = "f_171065_", remap = false) @Final private ModelPart rightHead;
    @Shadow(aliases = "f_171066_", remap = false) @Final private ModelPart leftHead;

    @Inject(method = {
        "prepareMobModel(Lnet/minecraft/world/entity/boss/wither/WitherBoss;FFF)V",
        "m_6839_(Lnet/minecraft/world/entity/boss/wither/WitherBoss;FFF)V"
    }, at = @At("TAIL"), remap = false)
    private void soulcatcherplus$smoothHeads(WitherBoss wither, float swing, float amount,
            float partialTick, CallbackInfo callback) {
        if (!CompanionBrain.isSoul(wither)) return;
        WitherHeadInterpolation heads = (WitherHeadInterpolation)wither;
        float body = Mth.rotLerp(partialTick, wither.yBodyRotO, wither.yBodyRot);
        soulcatcherplus$poseHead(rightHead, heads, 0, body, partialTick);
        soulcatcherplus$poseHead(leftHead, heads, 1, body, partialTick);
    }

    @Unique
    private static void soulcatcherplus$poseHead(ModelPart part, WitherHeadInterpolation heads,
            int index, float body, float partialTick) {
        part.yRot = Mth.wrapDegrees(heads.soulcatcherplus$headYaw(index, partialTick) - body) * Mth.DEG_TO_RAD;
        part.xRot = heads.soulcatcherplus$headPitch(index, partialTick) * Mth.DEG_TO_RAD;
    }
}
