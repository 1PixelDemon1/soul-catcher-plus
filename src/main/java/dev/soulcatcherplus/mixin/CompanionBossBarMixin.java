// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulBossBars;
import net.minecraft.world.server.ServerBossInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps owned bars invisible without changing their registered encounter participants. */
@Mixin(value = ServerBossInfo.class, remap = false)
public abstract class CompanionBossBarMixin implements SoulBossBars.Control {
    @Unique private boolean soulcatcherplus$suppressed;

    @Override public void soulcatcherplus$markSuppressed() { soulcatcherplus$suppressed = true; }

    // Official development name and verified Forge 1.16.5 SRG name. No refmap is needed.
    @Inject(method = {"setVisible(Z)V", "func_186758_d(Z)V"},
        at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void soulcatcherplus$preventShowing(boolean visible, CallbackInfo callback) {
        if (soulcatcherplus$suppressed && visible) callback.cancel();
    }
}
