// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.integration;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional data-driven guide: no Patchouli classes are linked by the core mod. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID)
public final class PatchouliGuide {
    private static final ResourceLocation BOOK_ITEM = new ResourceLocation("patchouli", "guide_book");
    private static final String BOOK_ID = SoulCatcherPlus.ID + ":soul_guide";
    private static final String RECEIVED = "scp_received_soul_guide";

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ModList.get().isLoaded("patchouli")) return;
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persistent.getBoolean(RECEIVED)) return;
        Item item = ForgeRegistries.ITEMS.getValue(BOOK_ITEM);
        if (item == null || item == Items.AIR) return;
        ItemStack book = new ItemStack(item);
        book.getOrCreateTag().putString("patchouli:book", BOOK_ID);
        // The book is unstackable; creative add() would otherwise destroy it when full.
        if (player.getInventory().getFreeSlot() >= 0) player.getInventory().add(book);
        if (!book.isEmpty()) {
            ItemEntity dropped = player.drop(book, false);
            if (dropped == null) return;
            dropped.setOwner(player.getUUID());
            dropped.setNoPickUpDelay();
        }
        persistent.putBoolean(RECEIVED, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void protectCreativeOverflow(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItem().getItem();
        if (!player.level.isClientSide && player.getAbilities().instabuild && player.getInventory().getFreeSlot() < 0
                && BOOK_ITEM.equals(stack.getItem().builtInRegistryHolder().key().location()) && stack.hasTag()
                && BOOK_ID.equals(stack.getTag().getString("patchouli:book"))) event.setCanceled(true);
    }

    private PatchouliGuide() {}
}
