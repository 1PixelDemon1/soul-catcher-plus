// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity.ai;

import dev.soulcatcherplus.pylon.SoulPylonBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/** Chooses a safe cardinal neighbour and keeps its stockpile out of the fetch loop. */
final class RavenPylonDrop {
    private RavenPylonDrop() {}

    static boolean inStockpile(BlockPos anchor, BlockPos item) {
        return Math.abs(anchor.getX() - item.getX()) <= 1 && Math.abs(anchor.getZ() - item.getZ()) <= 1
            && Math.abs(anchor.getY() - item.getY()) <= 2;
    }

    static Vector3d find(World world, BlockPos anchor) {
        BlockState pylon = world.getBlockState(anchor);
        if (!(pylon.getBlock() instanceof SoulPylonBlock)) return null;
        Direction front = pylon.getValue(SoulPylonBlock.FACING);
        Direction[] sides = {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()};
        Vector3d ground = null;
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

    private static boolean clear(World world, BlockPos cell) {
        if (cell.getY() < 0 || cell.getY() >= world.getHeight()) return false;
        BlockState state = world.getBlockState(cell);
        return !state.getFluidState().is(FluidTags.LAVA) && !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)
            && state.getCollisionShape(world, cell).isEmpty();
    }

    private static Vector3d dropAt(BlockPos cell) {
        return new Vector3d(cell.getX() + .5, cell.getY() + .15, cell.getZ() + .5);
    }
}
