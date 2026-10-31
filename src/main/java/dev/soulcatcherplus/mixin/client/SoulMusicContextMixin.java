// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import dev.soulcatcherplus.client.SoulMusic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Associate sounds emitted by native client ticks with the entity that requested them. */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class SoulMusicContextMixin {
    @Group(name = "soulcatcherplus$musicTick", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "m_104639_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;tick()V"), remap = false, require = 0)
    private void soulcatcherplus$tickOfficial(Entity entity) { SoulMusic.tickEntity(entity, false); }

    @Group(name = "soulcatcherplus$musicTick", min = 1, max = 1)
    @Redirect(method = {"tickNonPassenger", "m_104639_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_8119_()V"), remap = false, require = 0)
    private void soulcatcherplus$tickSrg(Entity entity) { SoulMusic.tickEntity(entity, false); }

    @Group(name = "soulcatcherplus$musicPassenger", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "m_104641_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;rideTick()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerOfficial(Entity entity) { SoulMusic.tickEntity(entity, true); }

    @Group(name = "soulcatcherplus$musicPassenger", min = 1, max = 1)
    @Redirect(method = {"tickPassenger", "m_104641_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;m_6083_()V"), remap = false, require = 0)
    private void soulcatcherplus$passengerSrg(Entity entity) { SoulMusic.tickEntity(entity, true); }
}
