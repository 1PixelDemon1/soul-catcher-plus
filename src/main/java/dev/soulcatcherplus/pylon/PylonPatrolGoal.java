// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.companion.CompanionBrain;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Short idle walks around the anchor; native attack goals take over as soon as a target appears. */
public final class PylonPatrolGoal extends Goal {
    private static final int RADIUS = 7, ATTEMPTS = 6;
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2, 3, -3};
    private final Mob mob;
    private Path path;
    private long nextWalkAt, stopAt;

    public PylonPatrolGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean idle() {
        return mob.getTarget() == null && CompanionBrain.mode(mob) == CompanionBrain.Mode.GUARD;
    }

    @Override public boolean canUse() {
        if (!idle() || mob.level().getGameTime() < nextWalkAt) return false;
        schedulePause();
        PylonBond bond = PylonSouls.bond(mob);
        if (bond == null || PylonSouls.paused(mob)) return false;
        BlockPos anchor = bond.position.above();
        boolean returnHome = mob.position().distanceToSqr(Vec3.atCenterOf(anchor)) > 10 * 10;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double radius = returnHome ? 2 : 3 + mob.getRandom().nextDouble() * (RADIUS - 3);
            BlockPos column = BlockPos.containing(anchor.getX() + .5 + Math.cos(angle) * radius,
                anchor.getY(), anchor.getZ() + .5 + Math.sin(angle) * radius);
            for (int height : HEIGHTS) {
                BlockPos point = column.offset(0, height, 0);
                if (point.getY() < mob.level().getMinY() || point.getY() >= mob.level().getMaxY()
                        || !mob.level().hasChunkAt(point) || !mob.level().getWorldBorder().isWithinBounds(point)
                        || !mob.getNavigation().isStableDestination(point)) continue;
                Path candidate = mob.getNavigation().createPath(point, 0);
                if (candidate != null && candidate.canReach() && !candidate.isDone()) {
                    path = candidate;
                    return true;
                }
                // Bound the expensive work to one path search per candidate column.
                break;
            }
        }
        return false;
    }

    @Override public void start() {
        stopAt = mob.level().getGameTime() + 160;
        mob.getNavigation().moveTo(path, .85);
    }
    @Override public boolean canContinueToUse() {
        return idle() && !mob.getNavigation().isDone() && mob.level().getGameTime() < stopAt;
    }
    @Override public void stop() {
        mob.getNavigation().stop();
        path = null;
        schedulePause();
    }
    private void schedulePause() { nextWalkAt = mob.level().getGameTime() + 60 + mob.getRandom().nextInt(81); }
}
