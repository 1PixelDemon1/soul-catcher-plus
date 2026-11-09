// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import dev.soulcatcherplus.SoulCatcherPlus;
import java.util.Random;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.util.NonNullList;

/** One scroll family with a separate, explicit soul payload for each forgotten companion. */
public final class ForgottenSoulScroll extends SoulScroll {
    public ForgottenSoulScroll() { super(SoulCatcherPlus.SOUL_RAVEN, Rarity.RARE); }

    private static EntityType<?>[] souls() {
        return new EntityType<?>[] {SoulCatcherPlus.SOUL_RAVEN.get(), SoulCatcherPlus.SOUL_COURIER.get(),
            SoulCatcherPlus.SOUL_SHAMAN.get()};
    }

    static boolean contains(String type) {
        for (EntityType<?> soul : souls()) if (soul.getRegistryName().toString().equals(type)) return true;
        return false;
    }

    public ItemStack randomPreset(Random random) {
        EntityType<?>[] available = souls();
        return preset(available[random.nextInt(available.length)]);
    }

    @Override public void fillItemCategory(ItemGroup group, NonNullList<ItemStack> items) {
        if (!allowdedIn(group)) return;
        for (EntityType<?> soul : souls()) items.add(preset(soul));
    }
}
