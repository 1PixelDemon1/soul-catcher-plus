// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import net.minecraft.world.level.block.EntityBlock;

import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public final class SoulAltarBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape TABLE = Shapes.or(box(0, 9, 0, 16, 12.6, 16),
        box(.5, 0, .5, 4.5, 9, 4.5), box(11.5, 0, .5, 15.5, 9, 4.5),
        box(.5, 0, 11.5, 4.5, 9, 15.5), box(11.5, 0, 11.5, 15.5, 9, 15.5));
    private static final VoxelShape[] SHAPES = {
        Shapes.or(TABLE, box(.5, 12, 8.5, 7.5, 16, 15.5)),
        Shapes.or(TABLE, box(.5, 12, .5, 7.5, 16, 7.5)),
        Shapes.or(TABLE, box(8.5, 12, .5, 15.5, 16, 7.5)),
        Shapes.or(TABLE, box(8.5, 12, 8.5, 15.5, 16, 15.5))
    };

    public SoulAltarBlock() {
        super(Properties.of().strength(3, 6).sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SoulAltarTile(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES[(state.getValue(FACING).get2DDataValue() + 2) % 4];
    }
    @Override public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer server && world.getBlockEntity(pos) instanceof SoulAltarTile altar) {
            NetworkHooks.openScreen(server, altar, pos);
            if (server.containerMenu instanceof SoulAltarMenu) SoulNetwork.sendAltarCollection(server);
        }
        return InteractionResult.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState old, Level world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide && world.getBlockEntity(pos) instanceof SoulAltarTile altar)
            altar.dropContents();
        super.onRemove(old, world, pos, next, moving);
    }
}
