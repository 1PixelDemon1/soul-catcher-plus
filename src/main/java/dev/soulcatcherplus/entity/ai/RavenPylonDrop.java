// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity.ai;

import dev.soulcatcherplus.pylon.SoulPylonBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/** Chooses a safe cardinal neighbour and keeps its stockpile out of the fetch loop. */
final class RavenPylonDrop {
    private RavenPylonDrop() {}

    static boolean inStockpile(BlockPos anchor, BlockPos item) {
        return Math.abs(anchor.getX() - item.getX()) <= 1 && Math.abs(anchor.getZ() - item.getZ()) <= 1
            && Math.abs(anchor.getY() - item.getY()) <= 2;
    }

    static Vec3 find(Level world, BlockPos anchor) {
        BlockState pylon = world.getBlockState(anchor);
        if (!(pylon.getBlock() instanceof SoulPylonBlock)) return null;
        Direction front = pylon.getValue(SoulPylonBlock.FACING);
        Direction[] sides = {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()};
        Vec3 ground = null;
        for (Direction side : sides) {
            BlockPos cell = anchor.relative(side);
            if (!world.hasChunkAt(cell)) continue;
            BlockState state = world.getBlockState(cell);
            if (state.getBlock() instanceof HopperBlock && clear(world, cell.above())) return dropAt(cell.above());
            if (!clear(world, cell)) continue;
            BlockState floor = world.getBlockState(cell.below());
            if (floor.getBlock() instanceof HopperBlock) return dropAt(cell);
            if (ground == null && Block.isFaceFull(floor.getCollisionShape(world, cell.below()), Direction.UP))
                ground = dropAt(cell);
        }
        return ground;
    }

    private static boolean clear(Level world, BlockPos cell) {
        if (cell.getY() < 0 || cell.getY() >= world.getHeight()) return false;
        BlockState state = world.getBlockState(cell);
        return !state.getFluidState().is(FluidTags.LAVA) && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
            && state.getCollisionShape(world, cell).isEmpty();
    }

    private static Vec3 dropAt(BlockPos cell) {
        return new Vec3(cell.getX() + .5, cell.getY() + .15, cell.getZ() + .5);
    }
}
