// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.SoulMusic;
import net.minecraft.client.audio.BackgroundMusicSelector;
import net.minecraft.client.audio.MusicTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A companion cannot replace or interrupt normal music through the shared vanilla manager. */
@Mixin(value = MusicTicker.class, remap = false)
public abstract class CompanionMusicMixin {
    @Inject(method = {"startPlaying", "func_239539_a_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$blockMusic(BackgroundMusicSelector music, CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) callback.cancel();
    }
    @Inject(method = {"stopPlaying", "func_209200_a"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveMusic(CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) callback.cancel();
    }
}
