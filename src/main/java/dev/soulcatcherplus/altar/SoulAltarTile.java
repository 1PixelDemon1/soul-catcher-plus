// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import net.minecraft.core.BlockPos;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class SoulAltarTile extends BlockEntity implements MenuProvider {
    static final int CAGE = 0, PAPER = 1, RESULT = 2;
    private static final String[] SLOT_KEYS = {"Cage", "Paper", "Result"};
    private final SimpleContainer contents = new SimpleContainer(SLOT_KEYS.length);
    private boolean dismantling;
    private ItemStack cageVisual = ItemStack.EMPTY, trayVisual = ItemStack.EMPTY;

    public SoulAltarTile(BlockPos pos, BlockState state) { super(SoulAltars.TILE.get(), pos, state);
        contents.addListener(inventory -> {
            if (dismantling || level == null || level.isClientSide) return;
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        });
    }

    SimpleContainer contents() { return contents; }
    public ItemStack cageVisual() { return cageVisual; }
    public ItemStack trayVisual() { return trayVisual; }

    @Override public Component getDisplayName() {
        return Component.translatable("block.soulcatcherplus.soul_transfer_altar");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SoulAltarMenu(id, inventory, this);
    }

    boolean accessible(Player player) {
        return !dismantling && !isRemoved() && player.level() == level && level.hasChunkAt(getBlockPos())
            && level.getBlockEntity(getBlockPos()) == this
            && player.distanceToSqr(getBlockPos().getX() + .5, getBlockPos().getY() + .5, getBlockPos().getZ() + .5) <= 64;
    }

    boolean inscribe(SoulEntry entry) {
        ItemStack cage = contents.getItem(CAGE), paper = contents.getItem(PAPER);
        int energy = SoulCage.energy(cage);
        if (dismantling || !(level instanceof ServerLevel world) || !contents.getItem(RESULT).isEmpty()
                || !(cage.getItem() instanceof SoulCage) || paper.getItem() != Items.PAPER || energy < entry.cost) return false;
        ItemStack scroll = SoulScroll.containing(entry);
        SoulCage.setEnergy(cage, energy - entry.cost);
        paper.shrink(1);
        if (paper.isEmpty()) contents.removeItemNoUpdate(PAPER);
        contents.setItem(RESULT, scroll);
        emitTransfer(world);
        return true;
    }

    private void emitTransfer(ServerLevel world) {
        var facing = getBlockState().getValue(SoulAltarBlock.FACING);
        var left = facing.getCounterClockWise();
        for (int i = 0; i < 5; i++) {
            double path = i / 4.0;
            double front = -.27 + .51 * Math.min(1, path * 2);
            double side = .25 - .34 * Math.max(0, path * 2 - 1);
            world.sendParticles(ParticleTypes.SOUL, getBlockPos().getX() + .5 + front * facing.getStepX() + side * left.getStepX(),
                getBlockPos().getY() + .82, getBlockPos().getZ() + .5 + front * facing.getStepZ() + side * left.getStepZ(),
                1, 0, .02, 0, .008);
        }
    }

    void dropContents() {
        dismantling = true;
        for (int i = 0; i < SLOT_KEYS.length; i++) Block.popResource(level, getBlockPos(), contents.removeItemNoUpdate(i));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        for (int i = 0; i < SLOT_KEYS.length; i++) tag.put(SLOT_KEYS[i], contents.getItem(i).save(new CompoundTag()));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            // Loading must not fire inventory listeners while the chunk is being rebuilt.
            switchLoadedStack(i, ItemStack.of(tag.getCompound(SLOT_KEYS[i])));
        }
    }

    private void switchLoadedStack(int slot, ItemStack stack) {
        contents.removeItemNoUpdate(slot);
        // The normal native setter fires listeners; suppress live notifications while restoring NBT.
        boolean previous = dismantling;
        dismantling = true;
        try { contents.setItem(slot, stack); }
        finally { dismantling = previous; }
    }

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ItemStack cage = contents.getItem(CAGE), tray = contents.getItem(RESULT);
        if (tray.isEmpty()) tray = contents.getItem(PAPER);
        // The renderer needs only item types, never the saved soul or scroll contents.
        if (!cage.isEmpty()) tag.put("CageVisual", new ItemStack(cage.getItem()).save(new CompoundTag()));
        if (!tray.isEmpty()) tag.put("TrayVisual", new ItemStack(tray.getItem()).save(new CompoundTag()));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag) { readVisuals(tag); }
    @Override public void onDataPacket(Connection network, ClientboundBlockEntityDataPacket packet) { readVisuals(packet.getTag()); }
    private void readVisuals(CompoundTag tag) {
        cageVisual = ItemStack.of(tag.getCompound("CageVisual"));
        trayVisual = ItemStack.of(tag.getCompound("TrayVisual"));
    }
    @Override public AABB getRenderBoundingBox() { return new AABB(getBlockPos()).expandTowards(0, .5, 0); }
}
