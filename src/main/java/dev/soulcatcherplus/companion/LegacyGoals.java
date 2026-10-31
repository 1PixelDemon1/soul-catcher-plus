// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;

/** The 1.16.5 selector has no public getter or removeAllGoals method. */
public final class LegacyGoals {
    public interface Access { Set<PrioritizedGoal> soulcatcherplus$goals(); }
    public static Stream<Goal> all(GoalSelector selector) {
        return ((Access)selector).soulcatcherplus$goals().stream().map(PrioritizedGoal::getGoal);
    }
    public static void clear(GoalSelector selector) {
        all(selector).collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(selector::removeGoal);
    }
    private LegacyGoals() {}
}
