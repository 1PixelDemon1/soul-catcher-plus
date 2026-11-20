// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

import net.minecraft.world.level.block.EntityBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** Opens the pylon's two-slot inventory; contents are managed by its server container. */
public final class SoulPylonBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(box(.96, 0, .96, 15.04, 1.5, 15.04),
        box(2, 1.5, 2, 14, 2.5, 14), box(3, 2.5, 3, 13, 4, 13),
        box(3.5, 4, 3.5, 12.5, 14, 12.5), box(1.46, 14, 1.46, 14.54, 16, 14.54));

    public SoulPylonBlock() {
        super(Properties.of().strength(3.5f, 6).sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SoulPylonTile(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!world.isClientSide && placer instanceof Player && world.getBlockEntity(pos) instanceof SoulPylonTile pylon)
            pylon.setOwner(placer.getUUID());
    }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!world.isClientSide && player instanceof ServerPlayer server
                && world.getBlockEntity(pos) instanceof SoulPylonTile pylon) pylon.open(server);
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != SoulPylons.TILE.get() ? null : (world, pos, current, tile) -> ((SoulPylonTile) tile).tick();
    }
    @Override public void onRemove(BlockState old, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide
                && world.getBlockEntity(pos) instanceof SoulPylonTile pylon) pylon.breakPylon();
        super.onRemove(old, world, pos, next, moving);
    }
}
