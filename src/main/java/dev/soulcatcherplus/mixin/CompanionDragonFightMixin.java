// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.event.VanillaBossEvents;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.world.end.DragonFightManager;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The End encounter must not adopt or delete a companion when searching for its real boss. */
@Mixin(value = DragonFightManager.class, remap = false)
public abstract class CompanionDragonFightMixin {
    @Group(name = "soulcatcherplus$encounterDragons", min = 2, max = 2)
    @Redirect(method = {"scanState", "func_210827_g", "findOrCreateDragon", "func_210828_h"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;getDragons()Ljava/util/List;"),
        remap = false, require = 0)
    private List<EnderDragonEntity> soulcatcherplus$encounterOfficial(ServerWorld world) { return soulcatcherplus$realDragons(world); }

    @Group(name = "soulcatcherplus$encounterDragons", min = 2, max = 2)
    @Redirect(method = {"scanState", "func_210827_g", "findOrCreateDragon", "func_210828_h"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ServerWorld;func_217439_j()Ljava/util/List;"),
        remap = false, require = 0)
    private List<EnderDragonEntity> soulcatcherplus$encounterSrg(ServerWorld world) { return soulcatcherplus$realDragons(world); }

    @Unique private static List<EnderDragonEntity> soulcatcherplus$realDragons(ServerWorld world) {
        return world.getDragons().stream().filter(dragon -> !VanillaBossEvents.isSoulBoss(dragon)).collect(Collectors.toList());
    }
}
