// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IWorldPosCallable;

/** Server-owned slots; the client gets its own inventory mirror through normal container packets. */
public final class SoulPylonContainer extends Container {
    public static final int CAGE_X = 66, SCROLL_X = 146, CONTENT_Y = 49;
    private static final int PLAYER_START = 2, PLAYER_END = 38;
    private final IInventory contents;
    private final IWorldPosCallable access;

    public SoulPylonContainer(int id, PlayerInventory player, PacketBuffer buffer) {
        this(id, player, new Inventory(2), IWorldPosCallable.create(player.player.level, buffer.readBlockPos()));
    }
    public SoulPylonContainer(int id, PlayerInventory player, SoulPylonTile pylon) {
        this(id, player, pylon, IWorldPosCallable.create(player.player.level, pylon.getBlockPos()));
    }
    private SoulPylonContainer(int id, PlayerInventory player, IInventory contents, IWorldPosCallable access) {
        super(SoulPylons.MENU.get(), id);
        checkContainerSize(contents, 2);
        this.contents = contents;
        this.access = access;
        addSlot(new PylonSlot(0, CAGE_X));
        addSlot(new PylonSlot(1, SCROLL_X));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, column + row * 9 + 9, 34 + column * 18, 119 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 34 + column * 18, 177));
    }

    public ItemStack cage() { return contents.getItem(0); }
    public boolean complete() { return !contents.getItem(0).isEmpty() && !contents.getItem(1).isEmpty(); }
    @Override public boolean stillValid(PlayerEntity player) {
        return contents.stillValid(player) && stillValid(access, player, SoulPylons.PYLON.get());
    }
    @Override public ItemStack quickMoveStack(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof SoulCage) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof SoulScroll) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else if (index < 29) {
            if (!moveItemStackTo(stack, 29, PLAYER_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, PLAYER_START, 29, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        source.onTake(player, stack);
        return original;
    }

    private final class PylonSlot extends Slot {
        private final int index;
        PylonSlot(int index, int x) { super(contents, index, x, CONTENT_Y); this.index = index; }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean mayPlace(ItemStack stack) {
            return (index == 0 ? stack.getItem() instanceof SoulCage : stack.getItem() instanceof SoulScroll)
                && contents.canPlaceItem(index, stack);
        }
        @Override public boolean mayPickup(PlayerEntity player) {
            return !player.isSpectator() && (!(contents instanceof SoulPylonTile pylon) || pylon.canEdit());
        }
    }
}
