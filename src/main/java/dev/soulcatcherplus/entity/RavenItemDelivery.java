// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps creative inventory overflow from deleting real loot carried by a raven. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class RavenItemDelivery {
    /** Version-matched native pickup restriction, distinct from the throwing entity. */
    public interface PickupTarget { java.util.UUID soulcatcherplus$pickupTarget(); }
    public static java.util.UUID pickupTarget(ItemEntity item) { return ((PickupTarget)item).soulcatcherplus$pickupTarget(); }
    private static final String PROTECTED_LOOT = "scp_raven_delivered";

    private RavenItemDelivery() {}

    public static void protect(ItemEntity item) { item.getPersistentData().putBoolean(PROTECTED_LOOT, true); }
    public static boolean isProtected(ItemEntity item) { return item.getPersistentData().getBooleanOr(PROTECTED_LOOT, false); }

    public static void deliver(ItemEntity item, ServerPlayer owner) {
        protect(item);
        item.setPos(owner.getX(), owner.getY() + 0.1, owner.getZ());
        // Keep native ownership, Forge pickup hooks and survival's partial insertion.
        item.playerTouch(owner);
    }

    @SubscribeEvent(priority = Priority.LOWEST)
    public static boolean guardCreativeOverflow(EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide() || !event.getEntity().getAbilities().instabuild
                || !isProtected(event.getItem())) return false;
        // Native creative add() destroys even the remainder of a partially inserted stack.
        // This must also guard later automatic collisions with loot left at the player's feet.
        if (!fitsWholeStack(event.getEntity().getInventory(), event.getItem().getItem())) return true;
            return false;
    }

    private static boolean fitsWholeStack(Inventory inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        // The native damaged-item branch uses only an empty main-inventory slot.
        if (incoming.isDamaged()) return inventory.getFreeSlot() >= 0;
        int remaining = incoming.getCount();
        for (ItemStack stored : inventory.getNonEquipmentItems()) {
            remaining -= stored.isEmpty()
                ? Math.min(incoming.getMaxStackSize(), inventory.getMaxStackSize())
                : mergeSpace(inventory, stored, incoming);
            if (remaining <= 0) return true;
        }
        // Vanilla can top up the offhand, but never chooses an empty offhand or armor slot.
        return remaining <= mergeSpace(inventory, inventory.getItem(Inventory.SLOT_OFFHAND), incoming);
    }

    private static int mergeSpace(Inventory inventory, ItemStack stored, ItemStack incoming) {
        if (stored.isEmpty() || !stored.isStackable() || stored.getItem() != incoming.getItem()
                || !ItemStack.isSameItemSameComponents(stored, incoming)) return 0;
        return Math.max(0, Math.min(stored.getMaxStackSize(), inventory.getMaxStackSize()) - stored.getCount());
    }
}
