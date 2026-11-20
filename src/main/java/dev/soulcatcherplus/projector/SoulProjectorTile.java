// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import net.minecraft.core.BlockPos;

import dev.soulcatcherplus.data.SoulEntry;
import dev.soulcatcherplus.data.SoulSnapshots;
import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;

/** Real scroll storage; the filtered display payload never contains the captured mob's full snapshot. */
public final class SoulProjectorTile extends BlockEntity implements WorldlyContainer, MenuProvider {
    private static final int[] NO_SLOTS = {};
    private ItemStack scroll = ItemStack.EMPTY;
    private CompoundTag projection = new CompoundTag();
    private ProjectorSettings settings = ProjectorSettings.DEFAULT;
    // A detached client-side visual is held by its tile, never by a global world-retaining cache.
    private Mob display;
    private boolean displayFailed, dismantling;

    public SoulProjectorTile(BlockPos pos, BlockState state) { super(SoulProjectors.TILE.get(), pos, state); }
    public ProjectorSettings settings() { return settings; }
    public void setSettings(ProjectorSettings value) {
        if (level instanceof ServerLevel && value.valid() && !value.equals(settings)) { settings = value; changed(); }
    }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
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
    @Override public boolean stillValid(Player player) {
        return !dismantling && !isRemoved() && level != null && player.level() == level
            && player.distanceToSqr(getBlockPos().getX() + .5, getBlockPos().getY() + .5, getBlockPos().getZ() + .5) <= 64
            && level.hasChunkAt(getBlockPos()) && level.getBlockEntity(getBlockPos()) == this;
    }
    public void dropContents() {
        dismantling = true;
        ItemStack removed = scroll;
        scroll = ItemStack.EMPTY;
        projection = new CompoundTag();
        Block.popResource(level, getBlockPos(), removed);
    }
    private void refreshProjection() {
        projection = new CompoundTag();
        if (level instanceof ServerLevel world && scroll.getItem() instanceof SoulScroll item) {
            SoulEntry entry = item.readSoul(scroll, world);
            if (entry != null) {
                try {
                    projection.putString("Type", entry.entityType);
                    projection.put("Appearance", SoulSnapshots.preview(entry));
                } catch (RuntimeException | java.io.IOException error) {
                    projection = new CompoundTag();
                    org.apache.logging.log4j.LogManager.getLogger().warn("Unable to prepare soul projection", error);
                }
            }
        }
        display = null;
        displayFailed = false;
    }
    /** Created on the client only, never added to a level and never AI-ticked. */
    public Mob displayEntity() {
        if (level == null || !level.isClientSide || projection.isEmpty() || displayFailed) return null;
        if (display != null && display.level() == level) return display;
        try {
            ResourceLocation id = ResourceLocation.tryParse(projection.getString("Type"));
            var type = id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id) ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
            var entity = type == null ? null : type.create(level);
            if (!(entity instanceof Mob mob)) { displayFailed = true; return null; }
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
        if (level != null && !level.isClientSide) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Scroll", scroll.save(new CompoundTag()));
        tag.put("Projection", projection.copy()); tag.put("Settings", settings.save());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        scroll = ItemStack.of(tag.getCompound("Scroll"));
        if (!(scroll.getItem() instanceof SoulScroll)) scroll = ItemStack.EMPTY;
        readVisuals(tag);
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Projection", projection.copy()); tag.put("Settings", settings.save());
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag) { readVisuals(tag); }
    @Override public void onDataPacket(Connection network, ClientboundBlockEntityDataPacket packet) { readVisuals(packet.getTag()); }
    private void readVisuals(CompoundTag tag) {
        CompoundTag incoming = tag.getCompound("Projection");
        if (!incoming.equals(projection)) { display = null; displayFailed = false; }
        projection = incoming.copy();
        settings = ProjectorSettings.load(tag.getCompound("Settings"));
    }
    @Override public AABB getRenderBoundingBox() { return new AABB(getBlockPos()).inflate(8); }
}
