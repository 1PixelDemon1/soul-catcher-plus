// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulSnapshots;
import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

/** Real scroll storage; the filtered display payload never contains the captured mob's full snapshot. */
public final class SoulProjectorTile extends TileEntity implements ISidedInventory, INamedContainerProvider {
    private static final int[] NO_SLOTS = {};
    private ItemStack scroll = ItemStack.EMPTY;
    private CompoundNBT projection = new CompoundNBT();
    private ProjectorSettings settings = ProjectorSettings.DEFAULT;
    // A detached client-side visual is held by its tile, never by a global world-retaining cache.
    private MobEntity display;
    private boolean displayFailed, dismantling;

    public SoulProjectorTile() { super(SoulProjectors.TILE.get()); }
    public ProjectorSettings settings() { return settings; }
    public void setSettings(ProjectorSettings value) {
        if (level instanceof ServerWorld && value.valid() && !value.equals(settings)) { settings = value; changed(); }
    }
    @Override public ITextComponent getDisplayName() { return new TranslationTextComponent(getBlockState().getBlock().getDescriptionId()); }
    @Override public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
        return new SoulProjectorContainer(id, inventory, this);
    }
    @Override public int getContainerSize() { return 1; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean isEmpty() { return scroll.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? scroll : ItemStack.EMPTY; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return !dismantling && !isRemoved() && slot == 0 && stack.getItem() instanceof SoulScroll;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (dismantling || isRemoved() || slot != 0 || !stack.isEmpty() && !(stack.getItem() instanceof SoulScroll)) return;
        scroll = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        if (!scroll.isEmpty()) scroll.setCount(1);
        refreshProjection();
        changed();
    }
    @Override public ItemStack removeItem(int slot, int count) {
        if (dismantling || isRemoved() || slot != 0 || count <= 0) return ItemStack.EMPTY;
        ItemStack removed = scroll.split(count);
        if (scroll.isEmpty()) scroll = ItemStack.EMPTY;
        refreshProjection();
        changed();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, 1); }
    @Override public void clearContent() { setItem(0, ItemStack.EMPTY); }
    @Override public int[] getSlotsForFace(Direction side) { return NO_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    @Override public boolean stillValid(PlayerEntity player) {
        return !dismantling && !isRemoved() && level != null && player.level == level
            && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64
            && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this;
    }
    public void dropContents() {
        dismantling = true;
        ItemStack removed = scroll;
        scroll = ItemStack.EMPTY;
        projection = new CompoundNBT();
        Block.popResource(level, worldPosition, removed);
    }
    private void refreshProjection() {
        projection = new CompoundNBT();
        if (level instanceof ServerWorld world && scroll.getItem() instanceof SoulScroll item) {
            SoulEntry entry = item.readSoul(scroll, world);
            if (entry != null) {
                try {
                    projection.putString("Type", entry.entityType);
                    projection.put("Appearance", SoulSnapshots.preview(entry));
                } catch (RuntimeException | java.io.IOException error) {
                    projection = new CompoundNBT();
                    org.apache.logging.log4j.LogManager.getLogger().warn("Unable to prepare soul projection", error);
                }
            }
        }
        display = null;
        displayFailed = false;
    }
    /** Created on the client only, never added to a level and never AI-ticked. */
    public MobEntity displayEntity() {
        if (level == null || !level.isClientSide || projection.isEmpty() || displayFailed) return null;
        if (display != null && display.level == level) return display;
        try {
            ResourceLocation id = ResourceLocation.tryParse(projection.getString("Type"));
            var type = id == null || !ForgeRegistries.ENTITIES.containsKey(id) ? null : ForgeRegistries.ENTITIES.getValue(id);
            var entity = type == null ? null : type.create(level);
            if (!(entity instanceof MobEntity mob)) { displayFailed = true; return null; }
            mob.load(SoulSnapshots.sanitize(projection.getCompound("Appearance")));
            mob.setNoAi(true);
            mob.setHealth(mob.getMaxHealth());
            mob.setPos(0, 0, 0);
            display = mob;
            return mob;
        } catch (RuntimeException error) {
            displayFailed = true;
            org.apache.logging.log4j.LogManager.getLogger().warn("Unable to construct soul projection", error);
            return null;
        }
    }
    public void markDisplayFailed() { displayFailed = true; }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        tag.put("Scroll", scroll.save(new CompoundNBT()));
        tag.put("Projection", projection.copy()); tag.put("Settings", settings.save());
        return tag;
    }
    @Override public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        scroll = ItemStack.of(tag.getCompound("Scroll"));
        if (!(scroll.getItem() instanceof SoulScroll)) scroll = ItemStack.EMPTY;
        readVisuals(tag);
    }
    @Override public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.save(new CompoundNBT());
        tag.put("Projection", projection.copy()); tag.put("Settings", settings.save());
        return tag;
    }
    @Override public SUpdateTileEntityPacket getUpdatePacket() { return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void handleUpdateTag(BlockState state, CompoundNBT tag) { readVisuals(tag); }
    @Override public void onDataPacket(NetworkManager network, SUpdateTileEntityPacket packet) { readVisuals(packet.getTag()); }
    private void readVisuals(CompoundNBT tag) {
        CompoundNBT incoming = tag.getCompound("Projection");
        if (!incoming.equals(projection)) { display = null; displayFailed = false; }
        projection = incoming.copy();
        settings = ProjectorSettings.load(tag.getCompound("Settings"));
    }
    @Override public AxisAlignedBB getRenderBoundingBox() { return new AxisAlignedBB(worldPosition).inflate(8); }
}
