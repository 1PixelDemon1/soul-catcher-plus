// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.furnace;

import net.minecraft.core.BlockPos;

import dev.soulcatcherplus.item.SoulCage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.items.ItemHandlerHelper;

public final class SoulFurnaceTile extends BlockEntity implements WorldlyContainer, MenuProvider {
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

    public SoulFurnaceTile(BlockPos pos, BlockState state) { super(SoulFurnaces.TILE.get(), pos, state); }
    public ItemStack cageVisual() { return cageVisual; }
    int progress() { return (int)(elapsed * 1000L / Math.max(1, duration)); }

    public void tick() {
        if (!(level instanceof ServerLevel world) || breaking) return;
        ItemStack input = items.get(INPUT), cage = items.get(CAGE);
        SmeltingRecipe recipe = input.isEmpty() ? null : world.getRecipeManager().getRecipeFor(RecipeType.SMELTING, this, world).orElse(null);
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
            world.setBlock(getBlockPos(), state.setValue(BlockStateProperties.LIT, working), 3);
    }

    void awardExperience(Vec3 position) {
        if (!(level instanceof ServerLevel world) || experience <= 0) return;
        int total = (int)Math.floor(experience);
        if (world.random.nextDouble() < experience - total) total++;
        experience = 0;
        setChanged();
        while (total > 0) {
            int value = ExperienceOrb.getExperienceValue(total);
            total -= value;
            world.addFreshEntity(new ExperienceOrb(world, position.x, position.y, position.z, value));
        }
    }

    void dropContents() {
        breaking = true;
        for (int i = 0; i < items.size(); i++) Block.popResource(level, getBlockPos(), items.set(i, ItemStack.EMPTY));
        awardExperience(Vec3.atCenterOf(getBlockPos()));
    }

    @Override public void setChanged() {
        super.setChanged();
        ItemStack cage = items.get(CAGE);
        Item type = cage.isEmpty() ? null : cage.getItem();
        if (type == syncedCageType) return;
        syncedCageType = type;
        if (!breaking && level instanceof ServerLevel)
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        int limit = Math.min(getMaxStackSize(), stack.getMaxStackSize());
        if (stack.getCount() > limit) stack.setCount(limit);
        setChanged();
    }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public boolean stillValid(Player player) {
        return !breaking && !isRemoved() && player.level == level && level.hasChunkAt(getBlockPos())
            && level.getBlockEntity(getBlockPos()) == this && player.distanceToSqr(Vec3.atCenterOf(getBlockPos())) <= 64;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT || slot == CAGE && stack.getItem() instanceof SoulCage;
    }
    @Override public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.UP ? TOP : direction == Direction.DOWN ? BOTTOM : SIDE;
    }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == OUTPUT; }
    @Override public Component getDisplayName() { return Component.translatable("block.soulcatcherplus.soul_fueled_furnace"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory player, Player entity) {
        return new SoulFurnaceMenu(id, player, this);
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("CookElapsed", elapsed);
        tag.putInt("CookDuration", duration);
        tag.putBoolean("CookPaid", cookPaid);
        tag.putDouble("StoredExperience", experience);
        if (cooking != null) tag.putString("Cooking", cooking.toString());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        elapsed = Math.max(0, tag.getInt("CookElapsed"));
        duration = Math.max(1, tag.getInt("CookDuration"));
        experience = Math.max(0, tag.getDouble("StoredExperience"));
        cooking = ResourceLocation.tryParse(tag.getString("Cooking"));
        // Older saves kept progress but still owed the soul for that operation.
        cookPaid = elapsed > 0 && cooking != null && tag.getBoolean("CookPaid");
        syncedCageType = items.get(CAGE).isEmpty() ? null : items.get(CAGE).getItem();
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        ItemStack cage = items.get(CAGE);
        if (!cage.isEmpty()) tag.put("CageVisual", new ItemStack(cage.getItem()).save(new CompoundTag()));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag) { readVisual(tag); }
    @Override public void onDataPacket(Connection network, ClientboundBlockEntityDataPacket packet) { readVisual(packet.getTag()); }
    private void readVisual(CompoundTag tag) { cageVisual = ItemStack.of(tag.getCompound("CageVisual")); }
}
