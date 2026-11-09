// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.NonNullList;

/** One scroll family with a separate, explicit soul payload for each forgotten companion. */
public final class ForgottenSoulScroll extends SoulScroll {
    public ForgottenSoulScroll() { super(SoulCatcherPlus.SOUL_RAVEN, new Properties().setId(SoulCatcherPlus.ITEMS.key("forgotten_soul_scroll")).rarity(Rarity.RARE)); }

    private static EntityType<?>[] souls() {
        return new EntityType<?>[] {SoulCatcherPlus.SOUL_RAVEN.get(), SoulCatcherPlus.SOUL_COURIER.get(),
            SoulCatcherPlus.SOUL_SHAMAN.get()};
    }

    static boolean contains(String type) {
        for (EntityType<?> soul : souls()) if (soul.builtInRegistryHolder().key().identifier().toString().equals(type)) return true;
        return false;
    }

    public ItemStack randomPreset(RandomSource random) {
        EntityType<?>[] available = souls();
        return preset(available[random.nextInt(available.length)]);
    }

    public java.util.List<ItemStack> creativeVariants() {
        var items = new java.util.ArrayList<ItemStack>();
        for (EntityType<?> soul : souls()) items.add(preset(soul));
        return items;
    }
}
