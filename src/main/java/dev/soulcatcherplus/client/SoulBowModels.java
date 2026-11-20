// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.util.ResourceLocation;

final class SoulBowModels {
    static void register() {
        var bow = SoulCatcherPlus.SOUL_ENERGY_BOW.get();
        ItemModelsProperties.register(bow, new ResourceLocation("pulling"), (stack, world, user) ->
            user != null && user.isUsingItem() && user.getUseItem() == stack ? 1 : 0);
        ItemModelsProperties.register(bow, new ResourceLocation("pull"), (stack, world, user) ->
            user != null && user.isUsingItem() && user.getUseItem() == stack
                ? (stack.getUseDuration() - user.getUseItemRemainingTicks()) / 20f : 0);
    }
    private SoulBowModels() {}
}
