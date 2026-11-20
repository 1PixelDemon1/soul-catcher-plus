// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.furnace;

import dev.soulcatcherplus.item.SoulCage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IIntArray;
import net.minecraft.util.IntArray;
import net.minecraft.util.math.BlockPos;

public final class SoulFurnaceMenu extends Container {
    private final IInventory contents;
    private final IIntArray data;
    private final BlockPos position;
    private static final int INVENTORY = 3, HOTBAR = 30, END = 39;

    public SoulFurnaceMenu(int id, PlayerInventory player, PacketBuffer buffer) {
        this(id, player, new Inventory(3), new IntArray(1), buffer.readBlockPos());
    }
    SoulFurnaceMenu(int id, PlayerInventory player, SoulFurnaceTile furnace) {
        this(id, player, furnace, new IIntArray() {
            @Override public int get(int index) { return furnace.progress(); }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 1; }
        }, furnace.getBlockPos());
    }
    private SoulFurnaceMenu(int id, PlayerInventory player, IInventory contents, IIntArray data, BlockPos position) {
        super(SoulFurnaces.MENU.get(), id);
        this.contents = contents;
        this.data = data;
        this.position = position;
        addSlot(new Slot(contents, 0, 47, 28));
        addSlot(new Slot(contents, 1, 47, 57) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SoulCage; }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(contents, 2, 131, 43) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public ItemStack onTake(PlayerEntity user, ItemStack stack) {
                if (contents instanceof SoulFurnaceTile furnace) furnace.awardExperience(user.position());
                return super.onTake(user, stack);
            }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, 9 + row * 9 + column, 17 + column * 18, 105 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 17 + column * 18, 163));
        addDataSlots(data);
    }

    public int energy() { return SoulCage.energy(contents.getItem(1)); }
    public int capacity() { return SoulCage.capacity(contents.getItem(1)); }
    public int progress() { return data.get(0); }
    @Override public boolean stillValid(PlayerEntity player) {
        return contents.stillValid(player) && player.level.hasChunkAt(position)
            && player.level.getBlockState(position).is(SoulFurnaces.BLOCK.get())
            && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(PlayerEntity player, int index) {
        if (index < 0 || index >= END || player.isSpectator() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        boolean moved;
        if (index < INVENTORY) moved = moveItemStackTo(stack, INVENTORY, END, true);
        else if (stack.getItem() instanceof SoulCage) moved = moveItemStackTo(stack, 1, 2, false);
        else if (player.level.getRecipeManager().getRecipeFor(IRecipeType.SMELTING, new Inventory(stack), player.level).isPresent())
            moved = moveItemStackTo(stack, 0, 1, false);
        else if (index < HOTBAR) moved = moveItemStackTo(stack, HOTBAR, END, false);
        else moved = moveItemStackTo(stack, INVENTORY, HOTBAR, false);
        if (!moved || stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
