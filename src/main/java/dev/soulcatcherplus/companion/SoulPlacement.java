// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Prefer open space, but allow soul placement inside blocks, water, and air. */
final class SoulPlacement {
    private static final int DIRECTIONS = 16, MAX_RADIUS = 16;
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2};

    static Vec3 findNear(Mob mob, ServerPlayer player, Vec3 center) {
        double radius = Math.min(MAX_RADIUS, Math.max(2, Math.ceil(mob.getBbWidth() / 2) + 1));
        Vec3 fallback = null;
        for (int height : HEIGHT_OFFSETS) for (int i = 0; i < DIRECTIONS; i++) {
            double angle = i * Math.PI * 2 / DIRECTIONS;
            Vec3 point = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            AABB bounds = mob.getBoundingBox().move(point.subtract(mob.position()));
            if (bounds.minY < mob.level().getMinBuildHeight() || bounds.maxY > mob.level().getMaxBuildHeight()
                    || !mob.level().getWorldBorder().isWithinBounds(bounds)
                    || !mob.level().hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) continue;
            if (fallback == null) fallback = point;
            if (!bounds.intersects(player.getBoundingBox()) && mob.level().noCollision(mob, bounds)) return point;
        }
        return fallback;
    }

    private SoulPlacement() {}
}
