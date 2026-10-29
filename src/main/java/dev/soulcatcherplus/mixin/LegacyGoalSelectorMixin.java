// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.LegacyGoals;
import java.util.Set;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GoalSelector.class, remap = false)
public abstract class LegacyGoalSelectorMixin implements LegacyGoals.Access {
    @Shadow(aliases = "field_220892_d", remap = false) @Final
    private Set<PrioritizedGoal> availableGoals;

    @Override public Set<PrioritizedGoal> soulcatcherplus$goals() { return availableGoals; }
}
