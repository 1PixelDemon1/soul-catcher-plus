// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import dev.soulcatcherplus.companion.SoulService;
import dev.soulcatcherplus.item.SoulCage;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulAltarMenu extends AbstractContainerMenu {
    public static final int CAGE_X = 176, PAPER_X = 219, RESULT_X = 271, INPUT_Y = 61;
    private static final int MAIN_START = 3, HOTBAR_START = 30, END = 39;
    private final SoulAltarTile altar;
    private final BlockPos pos;
    private final SimpleContainer contents;

    public SoulAltarMenu(int id, Inventory player, FriendlyByteBuf buffer) {
        this(id, player, null, buffer.readBlockPos(), new SimpleContainer(3));
    }

    SoulAltarMenu(int id, Inventory player, SoulAltarTile altar) {
        this(id, player, altar, altar.getBlockPos(), altar.contents());
    }

    private SoulAltarMenu(int id, Inventory player, SoulAltarTile altar, BlockPos pos, SimpleContainer contents) {
        super(SoulAltars.MENU.get(), id);
        this.altar = altar;
        this.pos = pos;
        this.contents = contents;
        addSlot(new Slot(contents, SoulAltarTile.CAGE, CAGE_X, INPUT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SoulCage; }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(contents, SoulAltarTile.PAPER, PAPER_X, INPUT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() == Items.PAPER; }
        });
        addSlot(new Slot(contents, SoulAltarTile.RESULT, RESULT_X, INPUT_Y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, 9 + row * 9 + column, 75 + column * 18, 151 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 75 + column * 18, 209));
    }

    @Override public boolean stillValid(Player player) {
        if (altar != null) return altar.accessible(player);
        return player.level().hasChunkAt(pos) && player.level().getBlockState(pos).is(SoulAltars.BLOCK.get())
            && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    public int energy() { return SoulCage.energy(contents.getItem(SoulAltarTile.CAGE)); }
    public int capacity() { return SoulCage.capacity(contents.getItem(SoulAltarTile.CAGE)); }
    public boolean hasPaper() { return !contents.getItem(SoulAltarTile.PAPER).isEmpty(); }
    public boolean resultOccupied() { return !contents.getItem(SoulAltarTile.RESULT).isEmpty(); }

    public void inscribe(ServerPlayer player, UUID id) {
        if (altar == null || !stillValid(player) || player.isSpectator()) return;
        var entry = SoulService.data(player).entries.get(id);
        if (entry == null) return;
        ResourceLocation type = ResourceLocation.tryParse(entry.entityType);
        if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(type)) return;
        if (altar.inscribe(entry)) broadcastChanges();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= END || player.isSpectator() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        boolean moved;
        if (index < MAIN_START) moved = moveItemStackTo(source, MAIN_START, END, true);
        else if (source.getItem() instanceof SoulCage) moved = moveItemStackTo(source, 0, 1, false);
        else if (source.getItem() == Items.PAPER) moved = moveItemStackTo(source, 1, 2, false);
        else if (index < HOTBAR_START) moved = moveItemStackTo(source, HOTBAR_START, END, false);
        else moved = moveItemStackTo(source, MAIN_START, HOTBAR_START, false);
        if (!moved || source.getCount() == original.getCount()) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
