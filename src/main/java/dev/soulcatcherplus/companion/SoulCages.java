// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.item.SoulCage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Offhand cages take priority, followed by the hotbar from left to right. */
public final class SoulCages {
    private static final int[] SLOTS = {Inventory.SLOT_OFFHAND, 0, 1, 2, 3, 4, 5, 6, 7, 8};
    public record Active(int slot, ItemStack stack) {
        public boolean present() { return slot >= 0; }
        public int energy() { return SoulCage.energy(stack); }
        public int capacity() { return SoulCage.capacity(stack); }
    }
    public static Active active(Player player) {
        for (int slot : SLOTS) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof SoulCage) return new Active(slot, stack);
        }
        return new Active(-1, ItemStack.EMPTY);
    }
    public static Component location(int slot) {
        if (slot < 0) return Component.translatable("screen.soulcatcherplus.cage_missing");
        if (slot == Inventory.SLOT_OFFHAND) return Component.translatable("screen.soulcatcherplus.cage_offhand");
        return Component.translatable("screen.soulcatcherplus.cage_slot", slot + 1);
    }
    public static boolean spend(ServerPlayer player, int amount) {
        Active active = active(player);
        if (amount < 0 || !active.present() || active.energy() < amount) return false;
        SoulCage.setEnergy(active.stack(), active.energy() - amount);
        player.getInventory().setChanged();
        return true;
    }
    public static int set(ServerPlayer player, int amount) {
        int stored = SoulCage.setEnergy(active(player).stack(), amount);
        player.getInventory().setChanged();
        return stored;
    }
    /** Incoming energy fills the offhand, then spills through the hotbar; spending never does. */
    public static int credit(ServerPlayer player, int amount) {
        if (amount <= 0) return 0;
        int remaining = amount;
        for (int slot : SLOTS) {
            if (remaining == 0) break;
            ItemStack stack = player.getInventory().getItem(slot);
            int stored = SoulCage.energy(stack);
            int accepted = Math.min(remaining, SoulCage.capacity(stack) - stored);
            if (accepted <= 0) continue;
            SoulCage.setEnergy(stack, stored + accepted);
            remaining -= accepted;
        }
        if (remaining < amount) player.getInventory().setChanged();
        return amount - remaining;
    }
    private SoulCages() {}
}
