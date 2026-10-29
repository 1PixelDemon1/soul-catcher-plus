// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.SoulSpawnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brackets native calls in try/finally, so an exception cannot leak a summoner into later spawns. */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class SoulSpawnContextMixin {
    // Official and Forge 1.20.1 SRG names; exactly one redirect in each group must match.
    @Group(name = "soulcatcherplus$tickSource", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "m_8647_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;tick()V"), remap = false, require = 0)
    private void soulcatcherplus$tickOfficial(Entity entity) { SoulSpawnContext.tick(entity, false); }

    @Group(name = "soulcatcherplus$tickSource", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "m_8647_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_8119_()V"), remap = false, require = 0)
    private void soulcatcherplus$tickSrg(Entity entity) { SoulSpawnContext.tick(entity, false); }

    @Group(name = "soulcatcherplus$passengerSource", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "m_8662_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;rideTick()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerOfficial(Entity entity) { SoulSpawnContext.tick(entity, true); }

    @Group(name = "soulcatcherplus$passengerSource", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "m_8662_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_6083_()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerSrg(Entity entity) { SoulSpawnContext.tick(entity, true); }
}
