// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.WitherHeadInterpolation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Vanilla already saves both samples; render them at the same time as the body. */
@Mixin(value = WitherBoss.class, remap = false)
public abstract class WitherHeadInterpolationMixin implements WitherHeadInterpolation {
    @Shadow(remap = false) @Final private float[] xRotHeads;
    @Shadow(remap = false) @Final private float[] yRotHeads;
    @Shadow(remap = false) @Final private float[] xRotOHeads;
    @Shadow(remap = false) @Final private float[] yRotOHeads;

    @Override public float soulcatcherplus$headYaw(int head, float partialTick) {
        return Mth.rotLerp(partialTick, yRotOHeads[head], yRotHeads[head]);
    }

    @Override public float soulcatcherplus$headPitch(int head, float partialTick) {
        return Mth.rotLerp(partialTick, xRotOHeads[head], xRotHeads[head]);
    }
}
