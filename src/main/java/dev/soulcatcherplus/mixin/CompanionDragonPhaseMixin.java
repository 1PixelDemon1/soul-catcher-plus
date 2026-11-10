// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulDragonPhase;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.IPhase;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse the synced hovering phase ID; this controller never visits native End waypoints. */
@Mixin(value = PhaseManager.class, remap = false)
public abstract class CompanionDragonPhaseMixin {
    @Shadow(aliases = "field_188760_b", remap = false) @Final private EnderDragonEntity dragon;
    @Unique private IPhase soulcatcherplus$phase;

    @Inject(method = {"getCurrentPhase", "func_188756_a"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$companionPhase(CallbackInfoReturnable<IPhase> callback) {
        if (!VanillaBossEvents.isSoulBoss(dragon)) return;
        if (soulcatcherplus$phase == null) soulcatcherplus$phase = new SoulDragonPhase(dragon);
        callback.setReturnValue(soulcatcherplus$phase);
    }
}
