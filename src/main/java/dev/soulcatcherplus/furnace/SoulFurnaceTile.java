// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.furnace;

import dev.soulcatcherplus.item.SoulCage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.items.ItemHandlerHelper;

public final class SoulFurnaceTile extends TileEntity implements ITickableTileEntity, ISidedInventory, INamedContainerProvider {
    static final int INPUT = 0, CAGE = 1, OUTPUT = 2;
    private static final int[] TOP = {INPUT}, SIDE = {CAGE}, BOTTOM = {OUTPUT};
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int elapsed, duration = 200;
    private boolean cookPaid;
    private double experience;
    private ResourceLocation cooking;
    private Item syncedCageType;
    private ItemStack cageVisual = ItemStack.EMPTY;
    private boolean breaking;

    public SoulFurnaceTile() { super(SoulFurnaces.TILE.get()); }
    public ItemStack cageVisual() { return cageVisual; }
    int progress() { return (int)(elapsed * 1000L / Math.max(1, duration)); }

    @Override public void tick() {
        if (!(level instanceof ServerWorld world) || breaking) return;
        ItemStack input = items.get(INPUT), cage = items.get(CAGE);
        FurnaceRecipe recipe = input.isEmpty() ? null : world.getRecipeManager().getRecipeFor(IRecipeType.SMELTING, this, world).orElse(null);
        ResourceLocation id = recipe == null ? null : recipe.getId();
        if (!java.util.Objects.equals(cooking, id)) {
            cooking = id;
            elapsed = 0;
            cookPaid = false;
            setChanged();
        }
        int energy = SoulCage.energy(cage);
        ItemStack result = recipe == null || (!cookPaid && energy == 0) ? ItemStack.EMPTY : recipe.assemble(this);
        ItemStack output = items.get(OUTPUT);
        boolean working = !result.isEmpty() && (output.isEmpty()
            ? result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize())
            : ItemHandlerHelper.canItemStacksStack(output, result)
                && output.getCount() <= Math.min(getMaxStackSize(), output.getMaxStackSize()) - result.getCount());
        if (working) {
            if (!cookPaid) {
                SoulCage.setEnergy(cage, energy - 1);
                cookPaid = true;
            }
            duration = Math.max(1, recipe.getCookingTime());
            if (++elapsed >= duration) {
                input.shrink(1);
                if (output.isEmpty()) items.set(OUTPUT, result.copy());
                else output.grow(result.getCount());
                experience += recipe.getExperience();
                elapsed = 0;
                cookPaid = false;
            }
            setChanged();
        }
        BlockState state = getBlockState();
        if (state.getValue(BlockStateProperties.LIT) != working)
            world.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, working), 3);
    }

    void awardExperience(Vector3d position) {
        if (!(level instanceof ServerWorld world) || experience <= 0) return;
        int total = (int)Math.floor(experience);
        if (world.random.nextDouble() < experience - total) total++;
        experience = 0;
        setChanged();
        while (total > 0) {
            int value = ExperienceOrbEntity.getExperienceValue(total);
            total -= value;
            world.addFreshEntity(new ExperienceOrbEntity(world, position.x, position.y, position.z, value));
        }
    }

    void dropContents() {
        breaking = true;
        for (int i = 0; i < items.size(); i++) Block.popResource(level, worldPosition, items.set(i, ItemStack.EMPTY));
        awardExperience(Vector3d.atCenterOf(worldPosition));
    }

    @Override public void setChanged() {
        super.setChanged();
        ItemStack cage = items.get(CAGE);
        Item type = cage.isEmpty() ? null : cage.getItem();
        if (type == syncedCageType) return;
        syncedCageType = type;
        if (!breaking && level instanceof ServerWorld)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ItemStackHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStackHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) stack.setCount(limit);
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(PlayerEntity player) {
        return !breaking && !isRemoved() && player.level == level && level.hasChunkAt(worldPosition)
            && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(Vector3d.atCenterOf(worldPosition)) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT || slot == CAGE && stack.getItem() instanceof SoulCage;
    }
    @Override public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.UP ? TOP : direction == Direction.DOWN ? BOTTOM : SIDE;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }
    @Override public ITextComponent getDisplayName() { return new TranslationTextComponent("block.soulcatcherplus.soul_fueled_furnace"); }
    @Override public Container createMenu(int id, PlayerInventory player, PlayerEntity entity) {
        return new SoulFurnaceMenu(id, player, this);
    }

    @Override public CompoundNBT save(CompoundNBT tag) {
        super.save(tag);
        ItemStackHelper.saveAllItems(tag, items);
        tag.putInt("CookElapsed", elapsed);
        tag.putInt("CookDuration", duration);
        tag.putBoolean("CookPaid", cookPaid);
        tag.putDouble("StoredExperience", experience);
        if (cooking != null) tag.putString("Cooking", cooking.toString());
        return tag;
    }
    @Override public void load(BlockState state, CompoundNBT tag) {
        super.load(state, tag);
        items.clear();
        ItemStackHelper.loadAllItems(tag, items);
        elapsed = Math.max(0, tag.getInt("CookElapsed"));
        duration = Math.max(1, tag.getInt("CookDuration"));
        experience = Math.max(0, tag.getDouble("StoredExperience"));
        cooking = ResourceLocation.tryParse(tag.getString("Cooking"));
        // Older saves kept progress but still owed the soul for that operation.
        cookPaid = elapsed > 0 && cooking != null && tag.getBoolean("CookPaid");
        syncedCageType = items.get(CAGE).isEmpty() ? null : items.get(CAGE).getItem();
    }
    @Override public CompoundNBT getUpdateTag() {
        CompoundNBT tag = super.save(new CompoundNBT());
        ItemStack cage = items.get(CAGE);
        if (!cage.isEmpty()) tag.put("CageVisual", new ItemStack(cage.getItem()).save(new CompoundNBT()));
        return tag;
    }
    @Override public SUpdateTileEntityPacket getUpdatePacket() { return new SUpdateTileEntityPacket(worldPosition, 0, getUpdateTag()); }
    @Override public void handleUpdateTag(BlockState state, CompoundNBT tag) { readVisual(tag); }
    @Override public void onDataPacket(NetworkManager network, SUpdateTileEntityPacket packet) { readVisual(packet.getTag()); }
    private void readVisual(CompoundNBT tag) { cageVisual = ItemStack.of(tag.getCompound("CageVisual")); }
}
