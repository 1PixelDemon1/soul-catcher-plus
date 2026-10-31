// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.SoulMusic;
import net.minecraft.sounds.Music;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A companion cannot replace or interrupt normal music through the shared vanilla manager. */
@Mixin(value = MusicManager.class, remap = false)
public abstract class CompanionMusicMixin {
    @Inject(method = {"startPlaying", "m_120184_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$blockMusic(Music music, CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) callback.cancel();
    }
    @Inject(method = {"stopPlaying()V", "m_120186_()V"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveMusic(CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) callback.cancel();
    }
    @Inject(method = {"stopPlaying(Lnet/minecraft/sounds/Music;)V", "m_278151_(Lnet/minecraft/sounds/Music;)V"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveSelectedMusic(Music music, CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) callback.cancel();
    }
}

