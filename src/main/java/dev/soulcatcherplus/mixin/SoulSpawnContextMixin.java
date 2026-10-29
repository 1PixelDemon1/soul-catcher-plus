// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulSpawnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brackets native calls in try/finally, so an exception cannot leak a summoner into later spawns. */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class SoulSpawnContextMixin {
    @Redirect(method = "tickNonPassenger", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;tick()V"), remap = false)
    private void soulcatcherplus$tick(Entity entity) { SoulSpawnContext.tick(entity, false); }

    @Redirect(method = "tickPassenger", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;rideTick()V"), remap = false)
    private void soulcatcherplus$passenger(Entity entity) { SoulSpawnContext.tick(entity, true); }
}
