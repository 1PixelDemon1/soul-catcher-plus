// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulSpawnContext;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the load/removal distinctions absent from Forge 1.16.5 entity events. */
@Mixin(value = Entity.class, remap = false)
public abstract class SoulEntityLifecycleMixin implements SoulSpawnContext.Lifecycle {
    @Unique private boolean soulcatcherplus$loaded;
    @Unique private boolean soulcatcherplus$destroyed;
    @Unique private boolean soulcatcherplus$changingDimension;

    @Inject(method = {"load", "func_70020_e"}, at = @At("HEAD"), remap = false)
    private void soulcatcherplus$loaded(CompoundNBT data, CallbackInfo callback) {
        soulcatcherplus$loaded = true;
        SoulSpawnContext.loaded((Entity)(Object)this);
    }
    @Inject(method = {"removeAfterChangingDimensions", "func_241204_bJ_"}, at = @At("HEAD"), remap = false)
    private void soulcatcherplus$dimension(CallbackInfo callback) { soulcatcherplus$changingDimension = true; }
    @Inject(method = "remove(Z)V", at = @At("HEAD"), remap = false)
    private void soulcatcherplus$removed(boolean keepData, CallbackInfo callback) {
        if (!soulcatcherplus$changingDimension && !SoulSpawnContext.unloading((Entity)(Object)this))
            soulcatcherplus$destroyed = true;
    }
    @Override public boolean soulcatcherplus$wasLoaded() { return soulcatcherplus$loaded; }
    @Override public boolean soulcatcherplus$wasDestroyed() { return soulcatcherplus$destroyed; }
    @Override public void soulcatcherplus$markFresh() {
        soulcatcherplus$loaded = soulcatcherplus$destroyed = soulcatcherplus$changingDimension = false;
    }
}
