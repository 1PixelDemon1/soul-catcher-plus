// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/** The display occupies half a block; the hologram never gets an entity hitbox. */
public final class SoulProjectorBlock extends Block {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 8, 16);
    public SoulProjectorBlock() {
        super(Properties.of(Material.STONE).strength(2.5f, 6).sound(SoundType.STONE).noOcclusion().lightLevel(state -> 3));
    }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new SoulProjectorTile(); }
    @Override public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) { return SHAPE; }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (player.isSecondaryUseActive()) return ActionResultType.PASS;
        if (player instanceof ServerPlayerEntity server && world.getBlockEntity(pos) instanceof SoulProjectorTile tile)
            net.minecraftforge.fml.network.NetworkHooks.openGui(server, tile, pos);
        return ActionResultType.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState old, World world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide
                && world.getBlockEntity(pos) instanceof SoulProjectorTile tile) tile.dropContents();
        super.onRemove(old, world, pos, next, moving);
    }
}
