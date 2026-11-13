// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

/** Prefer open space, but allow soul placement inside blocks, water, and air. */
final class SoulPlacement {
    private static final int DIRECTIONS = 16, MAX_RADIUS = 16;
    private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2};

    static Vector3d findNear(MobEntity mob, ServerPlayerEntity player, Vector3d center) {
        double radius = Math.min(MAX_RADIUS, Math.max(2, Math.ceil(mob.getBbWidth() / 2) + 1));
        Vector3d fallback = null;
        for (int height : HEIGHT_OFFSETS) for (int i = 0; i < DIRECTIONS; i++) {
            double angle = i * Math.PI * 2 / DIRECTIONS;
            Vector3d point = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            AxisAlignedBB bounds = mob.getBoundingBox().move(point.subtract(mob.position()));
            if (bounds.minY < 0 || bounds.maxY > mob.level.getMaxBuildHeight()
                    || !mob.level.getWorldBorder().isWithinBounds(bounds)
                    || !mob.level.hasChunksAt(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                        new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ))) continue;
            if (fallback == null) fallback = point;
            if (!bounds.intersects(player.getBoundingBox()) && mob.level.noCollision(mob, bounds)) return point;
        }
        return fallback;
    }

    private SoulPlacement() {}
}
