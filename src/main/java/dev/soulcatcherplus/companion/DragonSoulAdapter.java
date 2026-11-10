// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

/** A dragon keeps its native flight/parts, but no longer participates in the End encounter. */
public final class DragonSoulAdapter implements SoulAdapter {
    public interface Control { void soulcatcherplus$detachFight(); }

    @Override public void configureCompanion(Mob mob) {
        // Dragons do not tick ordinary navigation goals; SoulDragonPhase handles their movement.
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        EnderDragon dragon = (EnderDragon) mob;
        ((Control) dragon).soulcatcherplus$detachFight();
        dragon.nearestCrystal = null;
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
    }
}
