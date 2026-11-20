// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import dev.soulcatcherplus.item.SoulScroll;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.core.BlockPos;

/** One scroll slot; edits are coalesced to the latest value before the next container sync. */
public final class SoulProjectorContainer extends AbstractContainerMenu {
    private final Container contents;
    private final ContainerData values;
    private final Player viewer;
    private final BlockPos position;
    private ProjectorSettings pending;
    private long lastFlushTick = Long.MIN_VALUE;

    public SoulProjectorContainer(int id, Inventory player, FriendlyByteBuf buffer) {
        this(id, player, new SimpleContainer(1), new SimpleContainerData(6), buffer.readBlockPos());
    }
    public SoulProjectorContainer(int id, Inventory player, SoulProjectorTile tile) {
        this(id, player, tile, new ContainerData() {
            public int get(int index) {
                ProjectorSettings s = tile.settings();
                switch (index) {
                    case 0: return s.scale(); case 1: return s.color() & 0xFFFF;
                    case 2: return s.color() >>> 16; case 3: return s.angle();
                    case 4: return s.opacity(); case 5: return s.rotating() ? 1 : 0;
                    default: return 0;
                }
            }
            public void set(int index, int value) {}
            public int getCount() { return 6; }
        }, tile.getBlockPos());
    }
    private SoulProjectorContainer(int id, Inventory player, Container contents, ContainerData values, BlockPos position) {
        super(SoulProjectors.MENU.get(), id);
        checkContainerSize(contents, 1); checkContainerDataCount(values, 6);
        this.contents = contents; this.values = values; this.position = position; this.viewer = player.player;
        addSlot(new Slot(contents, 0, 40, 47) {
            public int getMaxStackSize() { return 1; }
            public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SoulScroll && contents.canPlaceItem(0, stack); }
            public boolean mayPickup(Player player) { return !player.isSpectator() && contents.stillValid(player); }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, column + row * 9 + 9, 69 + column * 18, 136 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 69 + column * 18, 194));
        addDataSlots(values);
    }
    public ProjectorSettings settings() {
        ProjectorSettings s = new ProjectorSettings(values.get(0), (values.get(1) & 0xFFFF) | ((values.get(2) & 255) << 16),
            values.get(3), values.get(4), values.get(5) != 0);
        return s.valid() ? s : ProjectorSettings.DEFAULT;
    }
    public void queueSettings(ProjectorSettings settings) { if (settings.valid()) pending = settings; }
    private void flushSettings(boolean closing) {
        if (pending == null) return;
        long tick = viewer.level().getGameTime();
        // Slot clicks can trigger multiple syncs per tick. Closing gets one final commit.
        if (!closing && tick == lastFlushTick) return;
        lastFlushTick = tick;
        if (!viewer.isSpectator() && stillValid(viewer) && contents instanceof SoulProjectorTile tile) tile.setSettings(pending);
        pending = null;
    }
    @Override public void broadcastChanges() { flushSettings(false); super.broadcastChanges(); }
    @Override public void removed(Player player) { flushSettings(true); super.removed(player); }
    @Override public boolean stillValid(Player player) {
        if (!contents.stillValid(player) || player.distanceToSqr(position.getX()+.5, position.getY()+.5, position.getZ()+.5) > 64
                || !player.level().hasChunkAt(position)) return false;
        return player.level().getBlockState(position).getBlock() instanceof SoulProjectorBlock;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0) { if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY; }
        else if (stack.getItem() instanceof SoulScroll) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 28) { if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
