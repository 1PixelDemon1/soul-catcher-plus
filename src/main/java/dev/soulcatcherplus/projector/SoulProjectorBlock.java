// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import net.minecraft.world.level.block.EntityBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** The display occupies half a block; the hologram never gets an entity hitbox. */
public final class SoulProjectorBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 8, 16);
    public SoulProjectorBlock() {
        super(Properties.of().strength(2.5f, 6).sound(SoundType.STONE).noOcclusion().lightLevel(state -> 3));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SoulProjectorTile(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && world.getBlockEntity(pos) instanceof SoulProjectorTile tile)
            net.minecraftforge.network.NetworkHooks.openScreen(server, tile, pos);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState old, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide
                && world.getBlockEntity(pos) instanceof SoulProjectorTile tile) tile.dropContents();
        super.onRemove(old, world, pos, next, moving);
    }
}
