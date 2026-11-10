// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapter;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/** Retain native skull attacks and defenses, with the owner's orders deciding every target. */
public final class WitherSoulAdapter implements SoulAdapter {
    @Override public void configureCompanion(Mob mob) {
        mob.targetSelector.removeAllGoals();
        mob.goalSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal).filter(goal -> goal instanceof FlightHoldGoal)
            .collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(mob.goalSelector::removeGoal);
        mob.goalSelector.addGoal(0, new FlightHoldGoal(mob));
        WitherBoss wither = (WitherBoss) mob;
        wither.setInvulnerableTicks(0);
        wither.setNoGravity(true);
        for (int head = 0; head < 3; head++) wither.setAlternativeTarget(head, 0);
    }

    /** Keep idle walking/ground navigation from fighting the custom flight controller. */
    private static final class FlightHoldGoal extends Goal {
        private final Mob mob;
        FlightHoldGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }
        @Override public boolean canUse() {
            return mob.getTarget() == null || CompanionBrain.mode(mob) == CompanionBrain.Mode.WAIT || CompanionBrain.isReturning(mob);
        }
        @Override public void start() { mob.getNavigation().stop(); }
        @Override public void tick() { mob.getNavigation().stop(); }
    }
}
