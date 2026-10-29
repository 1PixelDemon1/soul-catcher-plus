// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulSpawnContext;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brackets native calls in try/finally, so an exception cannot leak a summoner into later spawns. */
@Mixin(value = ServerWorld.class, remap = false)
public abstract class SoulSpawnContextMixin {
    // Official and Forge 1.16.5 SRG names; exactly one redirect in each group must match.
    @Group(name = "soulcatcherplus$tickSource", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "func_217479_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;tick()V"), remap = false, require = 0)
    private void soulcatcherplus$tickOfficial(Entity entity) { SoulSpawnContext.tick(entity, false); }

    @Group(name = "soulcatcherplus$tickSource", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "func_217479_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;func_70071_h_()V"), remap = false, require = 0)
    private void soulcatcherplus$tickSrg(Entity entity) { SoulSpawnContext.tick(entity, false); }

    @Group(name = "soulcatcherplus$passengerSource", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "func_217459_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;rideTick()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerOfficial(Entity entity) { SoulSpawnContext.tick(entity, true); }

    @Group(name = "soulcatcherplus$passengerSource", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "func_217459_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/Entity;func_70098_U()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerSrg(Entity entity) { SoulSpawnContext.tick(entity, true); }
    @Group(name = "soulcatcherplus$chunkUnload", min = 1, max = 1)
    @Redirect(method = {"unload", "func_217466_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/server/ServerWorld;onEntityRemoved(Lnet/minecraft/entity/Entity;)V"),
        remap = false, require = 0)
    private void soulcatcherplus$unload(ServerWorld world, Entity entity) { SoulSpawnContext.unload(world, entity); }

    @Group(name = "soulcatcherplus$chunkUnload", min = 1, max = 1)
    @Redirect(method = {"unload", "func_217466_a"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/server/ServerWorld;func_217484_g(Lnet/minecraft/entity/Entity;)V"),
        remap = false, require = 0)
    private void soulcatcherplus$unloadSrg(ServerWorld world, Entity entity) { SoulSpawnContext.unload(world, entity); }
}
