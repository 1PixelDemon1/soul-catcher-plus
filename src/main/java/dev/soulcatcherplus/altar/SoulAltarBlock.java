// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

public final class SoulAltarBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape TABLE = VoxelShapes.or(box(0, 9, 0, 16, 12.6, 16),
        box(.5, 0, .5, 4.5, 9, 4.5), box(11.5, 0, .5, 15.5, 9, 4.5),
        box(.5, 0, 11.5, 4.5, 9, 15.5), box(11.5, 0, 11.5, 15.5, 9, 15.5));
    private static final VoxelShape[] SHAPES = {
        VoxelShapes.or(TABLE, box(.5, 12, 8.5, 7.5, 16, 15.5)),
        VoxelShapes.or(TABLE, box(.5, 12, .5, 7.5, 16, 7.5)),
        VoxelShapes.or(TABLE, box(8.5, 12, .5, 15.5, 16, 7.5)),
        VoxelShapes.or(TABLE, box(8.5, 12, 8.5, 15.5, 16, 15.5))
    };

    public SoulAltarBlock() {
        super(Properties.of(Material.STONE).strength(3, 6).sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new SoulAltarTile(); }
    @Override public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) {
        return SHAPES[(state.getValue(FACING).get2DDataValue() + 2) % 4];
    }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        if (player.isSecondaryUseActive()) return ActionResultType.PASS;
        if (player instanceof ServerPlayerEntity server && world.getBlockEntity(pos) instanceof SoulAltarTile altar) {
            NetworkHooks.openGui(server, altar, pos);
            if (server.containerMenu instanceof SoulAltarMenu) SoulNetwork.sendAltarCollection(server);
        }
        return ActionResultType.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState old, World world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide && world.getBlockEntity(pos) instanceof SoulAltarTile altar)
            altar.dropContents();
        super.onRemove(old, world, pos, next, moving);
    }
}
