// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import com.google.common.collect.ImmutableList;
import dev.soulcatcherplus.api.SoulAdapter;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.schedule.Activity;

/** Native Warden attacks driven by companion orders, without the wild encounter lifecycle. */
public final class WardenSoulAdapter implements SoulAdapter {
    @Override public CompoundTag capture(Mob mob) {
        CompoundTag tag = SoulAdapter.super.capture(mob);
        tag.remove("anger");
        tag.remove("listener");
        return tag;
    }

    @Override public void configureCompanion(Mob mob) {
        CompanionBrain.installStandardGoals(mob);
        configureAttacks((Warden)mob);
    }

    @Override public void configureMinion(Mob mob) {
        mob.targetSelector.removeAllGoals();
        configureAttacks((Warden)mob);
    }

    private static void configureAttacks(Warden warden) {
        Brain<Warden> brain = warden.getBrain();
        // Do not stop the old behaviors: Digging.stop() discards its owner.
        brain.removeAllBehaviors();
        brain.getMemories().keySet().forEach(brain::eraseMemory);
        brain.setMemory(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE);
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new Swim(0.8F), new SonicBoom(), new MeleeAttack(18)));
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.CORE);
        brain.setActiveActivityIfPossible(Activity.CORE);
        warden.setPose(Pose.STANDING);
        warden.goalSelector.getAvailableGoals().stream().map(net.minecraft.world.entity.ai.goal.WrappedGoal::getGoal)
            .filter(goal -> goal instanceof ChaseGoal)
            .collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(warden.goalSelector::removeGoal);
        warden.goalSelector.addGoal(1, new ChaseGoal(warden));
    }

    /** Reuse native pathfinding/look control; the Brain supplies both attacks and their cooldowns. */
    private static final class ChaseGoal extends MeleeAttackGoal {
        ChaseGoal(Warden warden) { super(warden, 1.2, true); }
        @Override protected void checkAndPerformAttack(LivingEntity target, double distance) {
            // Native MeleeAttack observes the Sonic Boom charge and cannot interrupt it.
        }
    }
}
