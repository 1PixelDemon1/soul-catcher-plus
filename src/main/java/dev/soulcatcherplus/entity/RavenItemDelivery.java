// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps creative inventory overflow from deleting real loot carried by a raven. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class RavenItemDelivery {
    private static final String PROTECTED_LOOT = "scp_raven_delivered";

    private RavenItemDelivery() {}

    public static void protect(ItemEntity item) { item.getPersistentData().putBoolean(PROTECTED_LOOT, true); }
    public static boolean isProtected(ItemEntity item) { return item.getPersistentData().getBoolean(PROTECTED_LOOT); }

    public static void deliver(ItemEntity item, ServerPlayerEntity owner) {
        protect(item);
        item.setPos(owner.getX(), owner.getY() + 0.1, owner.getZ());
        // Keep native ownership, Forge pickup hooks and survival's partial insertion.
        item.playerTouch(owner);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void guardCreativeOverflow(EntityItemPickupEvent event) {
        if (event.getPlayer().level.isClientSide || !event.getPlayer().abilities.instabuild
                || !isProtected(event.getItem())) return;
        // Native creative add() destroys even the remainder of a partially inserted stack.
        // This must also guard later automatic collisions with loot left at the player's feet.
        if (!fitsWholeStack(event.getPlayer().inventory, event.getItem().getItem())) event.setCanceled(true);
    }

    private static boolean fitsWholeStack(PlayerInventory inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        // The native damaged-item branch uses only an empty main-inventory slot.
        if (incoming.isDamaged()) return inventory.getFreeSlot() >= 0;
        int remaining = incoming.getCount();
        for (ItemStack stored : inventory.items) {
            remaining -= stored.isEmpty()
                ? Math.min(incoming.getMaxStackSize(), inventory.getMaxStackSize())
                : mergeSpace(inventory, stored, incoming);
            if (remaining <= 0) return true;
        }
        // Vanilla can top up the offhand, but never chooses an empty offhand or armor slot.
        return remaining <= mergeSpace(inventory, inventory.offhand.get(0), incoming);
    }

    private static int mergeSpace(PlayerInventory inventory, ItemStack stored, ItemStack incoming) {
        if (stored.isEmpty() || !stored.isStackable() || stored.getItem() != incoming.getItem()
                || !ItemStack.tagMatches(stored, incoming)) return 0;
        return Math.max(0, Math.min(stored.getMaxStackSize(), inventory.getMaxStackSize()) - stored.getCount());
    }
}
