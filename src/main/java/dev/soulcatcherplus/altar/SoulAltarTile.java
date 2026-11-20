// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;

public final class SoulAltarTile extends TileEntity implements INamedContainerProvider {
    static final int CAGE = 0, PAPER = 1, RESULT = 2;
    private static final String[] SLOT_KEYS = {"Cage", "Paper", "Result"};
    private final Inventory contents = new Inventory(SLOT_KEYS.length);
    private boolean dismantling;
    private ItemStack cageVisual = ItemStack.EMPTY, trayVisual = ItemStack.EMPTY;

    public SoulAltarTile() {
        super(SoulAltars.TILE.get());
        contents.addListener(inventory -> {
            if (dismantling || level == null || level.isClientSide) return;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        });
    }

    Inventory contents() { return contents; }
    public ItemStack cageVisual() { return cageVisual; }
    public ItemStack trayVisual() { return trayVisual; }

    @Override public ITextComponent getDisplayName() {
        return new TranslationTextComponent("block.soulcatcherplus.soul_transfer_altar");
    }
    @Override public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
        return new SoulAltarMenu(id, inventory, this);
    }

    boolean accessible(PlayerEntity player) {
        return !dismantling && !isRemoved() && player.level == level && level.hasChunkAt(worldPosition)
            && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }

    boolean inscribe(SoulEntry entry) {
        ItemStack cage = contents.getItem(CAGE), paper = contents.getItem(PAPER);
        int energy = SoulCage.energy(cage);
        if (dismantling || !(level instanceof ServerWorld world) || !contents.getItem(RESULT).isEmpty()
                || !(cage.getItem() instanceof SoulCage) || paper.getItem() != Items.PAPER || energy < entry.cost) return false;
        ItemStack scroll = SoulScroll.containing(entry);
        SoulCage.setEnergy(cage, energy - entry.cost);
        paper.shrink(1);
        if (paper.isEmpty()) contents.removeItemNoUpdate(PAPER);
        contents.setItem(RESULT, scroll);
        emitTransfer(world);
        return true;
    }

    private void emitTransfer(ServerWorld world) {
        var facing = getBlockState().getValue(SoulAltarBlock.FACING);
        var left = facing.getCounterClockWise();
        for (int i = 0; i < 5; i++) {
            double path = i / 4.0;
            double front = -.27 + .51 * Math.min(1, path * 2);
            double side = .25 - .34 * Math.max(0, path * 2 - 1);
            world.sendParticles(ParticleTypes.SOUL, worldPosition.getX() + .5 + front * facing.getStepX() + side * left.getStepX(),
                worldPosition.getY() + .82, worldPosition.getZ() + .5 + front * facing.getStepZ() + side * left.getStepZ(),
                1, 0, .02, 0, .008);
        }
    }

    void dropContents() {
        dismantling = true;
        for (int i = 0; i < SLOT_KEYS.length; i++) Block.popResource(level, worldPosition, contents.removeItemNoUpdate(i));
    }

    @Override public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        for (int i = 0; i < SLOT_KEYS.length; i++) tag.put(SLOT_KEYS[i], contents.getItem(i).save(new CompoundNBT()));
        return tag;
    }

    @Override public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
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

    @Override public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.save(new CompoundNBT());
        ItemStack cage = contents.getItem(CAGE), tray = contents.getItem(RESULT);
        if (tray.isEmpty()) tray = contents.getItem(PAPER);
        // The renderer needs only item types, never the saved soul or scroll contents.
        if (!cage.isEmpty()) tag.put("CageVisual", new ItemStack(cage.getItem()).save(new CompoundNBT()));
        if (!tray.isEmpty()) tag.put("TrayVisual", new ItemStack(tray.getItem()).save(new CompoundNBT()));
        return tag;
    }
    @Override public SUpdateTileEntityPacket getUpdatePacket() { return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void handleUpdateTag(BlockState state, CompoundNBT tag) { readVisuals(tag); }
    @Override public void onDataPacket(NetworkManager network, SUpdateTileEntityPacket packet) { readVisuals(packet.getTag()); }
    private void readVisuals(CompoundNBT tag) {
        cageVisual = ItemStack.of(tag.getCompound("CageVisual"));
        trayVisual = ItemStack.of(tag.getCompound("TrayVisual"));
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(worldPosition).expandTowards(0, .5, 0); }
}
