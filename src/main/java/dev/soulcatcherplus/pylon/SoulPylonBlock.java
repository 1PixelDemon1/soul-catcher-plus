// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/** Opens the pylon's two-slot inventory; contents are managed by its server container. */
public final class SoulPylonBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = VoxelShapes.or(box(.96, 0, .96, 15.04, 1.5, 15.04),
        box(2, 1.5, 2, 14, 2.5, 14), box(3, 2.5, 3, 13, 4, 13),
        box(3.5, 4, 3.5, 12.5, 14, 12.5), box(1.46, 14, 1.46, 14.54, 16, 14.54));

    public SoulPylonBlock() {
        super(Properties.of(Material.STONE).strength(3.5f, 6).sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockItemUseContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public boolean hasTileEntity(BlockState state) { return true; }
    @Override public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new SoulPylonTile(); }
    @Override public VoxelShape getShape(BlockState state, IBlockReader world, BlockPos pos, ISelectionContext context) { return SHAPE; }
    @Override public void setPlacedBy(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!world.isClientSide && placer instanceof PlayerEntity && world.getBlockEntity(pos) instanceof SoulPylonTile pylon)
            pylon.setOwner(placer.getUUID());
    }
    @Override public ActionResultType use(BlockState state, World world, BlockPos pos, PlayerEntity player,
            Hand hand, BlockRayTraceResult hit) {
        if (player.isSecondaryUseActive()) return ActionResultType.PASS;
        if (!world.isClientSide && player instanceof ServerPlayerEntity server
                && world.getBlockEntity(pos) instanceof SoulPylonTile pylon) pylon.open(server);
        return ActionResultType.sidedSuccess(world.isClientSide);
    }
    @Override public void onRemove(BlockState old, World world, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && !world.isClientSide
                && world.getBlockEntity(pos) instanceof SoulPylonTile pylon) pylon.breakPylon();
        super.onRemove(old, world, pos, next, moving);
    }
}
