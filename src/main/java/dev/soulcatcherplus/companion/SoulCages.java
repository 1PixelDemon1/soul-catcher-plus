// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.item.SoulCage;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Offhand cages take priority, followed by the hotbar from left to right. */
public final class SoulCages {
    private static final int OFFHAND_SLOT = 40;
    private static final int[] SLOTS = {OFFHAND_SLOT, 0, 1, 2, 3, 4, 5, 6, 7, 8};
    @com.github.bsideup.jabel.Desugar
    public record Active(int slot, ItemStack stack) {
        public boolean present() { return slot >= 0; }
        public int energy() { return SoulCage.energy(stack); }
        public int capacity() { return SoulCage.capacity(stack); }
    }
    public static Active active(PlayerEntity player) {
        for (int slot : SLOTS) {
            ItemStack stack = player.inventory.getItem(slot);
            if (stack.getItem() instanceof SoulCage) return new Active(slot, stack);
        }
        return new Active(-1, ItemStack.EMPTY);
    }
    public static ITextComponent location(int slot) {
        if (slot < 0) return new net.minecraft.util.text.TranslationTextComponent("screen.soulcatcherplus.cage_missing");
        if (slot == OFFHAND_SLOT) return new net.minecraft.util.text.TranslationTextComponent("screen.soulcatcherplus.cage_offhand");
        return new net.minecraft.util.text.TranslationTextComponent("screen.soulcatcherplus.cage_slot", slot + 1);
    }
    public static boolean spend(ServerPlayerEntity player, int amount) {
        Active active = active(player);
        if (amount < 0 || !active.present() || active.energy() < amount) return false;
        SoulCage.setEnergy(active.stack(), active.energy() - amount);
        player.inventory.setChanged();
        return true;
    }
    public static int set(ServerPlayerEntity player, int amount) {
        int stored = SoulCage.setEnergy(active(player).stack(), amount);
        player.inventory.setChanged();
        return stored;
    }
    /** Incoming energy fills the offhand, then spills through the hotbar; spending never does. */
    public static int credit(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return 0;
        int remaining = amount;
        for (int slot : SLOTS) {
            if (remaining == 0) break;
            ItemStack stack = player.inventory.getItem(slot);
            int stored = SoulCage.energy(stack);
            int accepted = Math.min(remaining, SoulCage.capacity(stack) - stored);
            if (accepted <= 0) continue;
            SoulCage.setEnergy(stack, stored + accepted);
            remaining -= accepted;
        }
        if (remaining < amount) player.inventory.setChanged();
        return amount - remaining;
    }
    private SoulCages() {}
}
