// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;

/** A dragon keeps its native flight/parts, but no longer participates in the End encounter. */
public final class DragonSoulAdapter implements SoulAdapter {
    public interface Control { void soulcatcherplus$detachFight(); }

    @Override public void configureCompanion(MobEntity mob) {
        // Dragons do not tick ordinary navigation goals; SoulDragonPhase handles their movement.
        LegacyGoals.clear(mob.goalSelector);
        LegacyGoals.clear(mob.targetSelector);
        EnderDragonEntity dragon = (EnderDragonEntity) mob;
        ((Control) dragon).soulcatcherplus$detachFight();
        dragon.nearestCrystal = null;
        dragon.getPhaseManager().setPhase(PhaseType.HOVERING);
    }
}
