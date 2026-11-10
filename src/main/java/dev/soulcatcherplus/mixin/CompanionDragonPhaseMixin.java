// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulDragonPhase;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse the synced hovering phase ID; this controller never visits native End waypoints. */
@Mixin(value = EnderDragonPhaseManager.class, remap = false)
public abstract class CompanionDragonPhaseMixin {
    @Shadow(remap = false) @Final private EnderDragon dragon;
    @Unique private DragonPhaseInstance soulcatcherplus$phase;

    @Inject(method = "getCurrentPhase", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$companionPhase(CallbackInfoReturnable<DragonPhaseInstance> callback) {
        if (!VanillaBossEvents.isSoulBoss(dragon)) return;
        if (soulcatcherplus$phase == null) soulcatcherplus$phase = new SoulDragonPhase(dragon);
        callback.setReturnValue(soulcatcherplus$phase);
    }
}
