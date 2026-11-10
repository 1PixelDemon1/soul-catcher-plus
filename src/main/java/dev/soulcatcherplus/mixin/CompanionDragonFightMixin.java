// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.event.VanillaBossEvents;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The End encounter must not adopt or delete a companion when searching for its real boss. */
@Mixin(value = EnderDragonFight.class, remap = false)
public abstract class CompanionDragonFightMixin {
    @Redirect(method = {"scanState", "findOrCreateDragon"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getDragons()Ljava/util/List;"), remap = false, require = 2)
    private List<EnderDragon> soulcatcherplus$encounter(net.minecraft.server.level.ServerLevel world) { return soulcatcherplus$realDragons(world); }

    @Unique private static List<EnderDragon> soulcatcherplus$realDragons(ServerLevel world) {
        return world.getDragons().stream().filter(dragon -> !VanillaBossEvents.isSoulBoss(dragon)).collect(Collectors.toList());
    }
}
