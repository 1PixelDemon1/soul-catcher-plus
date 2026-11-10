// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.event.VanillaBossEvents;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The End encounter must not adopt or delete a companion when searching for its real boss. */
@Mixin(value = EndDragonFight.class, remap = false)
public abstract class CompanionDragonFightMixin {
    @Group(name = "soulcatcherplus$encounterDragons", min = 2, max = 2)
    @Redirect(method = {"scanState", "m_64102_", "findOrCreateDragon", "m_64103_"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getDragons()Ljava/util/List;"),
        remap = false, require = 0)
    private List<EnderDragon> soulcatcherplus$encounterOfficial(ServerLevel world) { return soulcatcherplus$realDragons(world); }

    @Group(name = "soulcatcherplus$encounterDragons", min = 2, max = 2)
    @Redirect(method = {"scanState", "m_64102_", "findOrCreateDragon", "m_64103_"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8857_()Ljava/util/List;"),
        remap = false, require = 0)
    private List<EnderDragon> soulcatcherplus$encounterSrg(ServerLevel world) { return soulcatcherplus$realDragons(world); }

    @Unique private static List<EnderDragon> soulcatcherplus$realDragons(ServerLevel world) {
        return world.getDragons().stream().filter(dragon -> !VanillaBossEvents.isSoulBoss(dragon)).collect(Collectors.toList());
    }
}
