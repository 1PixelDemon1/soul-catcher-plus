// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.SoulMusic;
import dev.soulcatcherplus.companion.CompanionBrain;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aquamirae 5.4.0 updates a shared cooldown before calling the vanilla music manager. */
@Pseudo
@Mixin(targets = "com.obscuria.aquamirae.client.AquamiraeAmbient", remap = false)
public abstract class AquamiraeCompanionMusicMixin {
    @Shadow private static int corneliaMusic;
    @Unique private static boolean soulcatcherplus$musicWasReady;
    @Unique private static WeakReference<Entity> soulcatcherplus$lastMusicSource;

    @Inject(method = "playCorneliaMusic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void soulcatcherplus$preserveBossCooldown(PlayerEntity player, CallbackInfo callback) {
        if (SoulMusic.suppressCurrentEntity()) { callback.cancel(); return; }
        soulcatcherplus$musicWasReady = corneliaMusic <= 0;
    }
    @Inject(method = "playCorneliaMusic", at = @At("RETURN"), remap = false)
    private static void soulcatcherplus$rememberMusicSource(PlayerEntity player, CallbackInfo callback) {
        // Only a successful start replaces ownership. A nearby boss that merely hits the cooldown does not.
        if (soulcatcherplus$musicWasReady && corneliaMusic > 0)
            soulcatcherplus$lastMusicSource = new WeakReference<>(SoulMusic.currentEntity());
    }
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void soulcatcherplus$releaseCompanionCooldown(TickEvent.ClientTickEvent event, CallbackInfo callback) {
        if (event.phase != TickEvent.Phase.END || soulcatcherplus$lastMusicSource == null) return;
        Entity source = soulcatcherplus$lastMusicSource.get();
        if (source == null || source.level != Minecraft.getInstance().level || corneliaMusic <= 0) {
            soulcatcherplus$lastMusicSource = null;
        } else if (CompanionBrain.isSoul(source)) {
            // A late ownership packet must not leave this companion's shared 150-second timer behind.
            corneliaMusic = 0;
            soulcatcherplus$lastMusicSource = null;
        }
    }
}
