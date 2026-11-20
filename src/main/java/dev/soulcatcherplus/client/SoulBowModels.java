// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

final class SoulBowModels {
    static void register() {
        var bow = SoulCatcherPlus.SOUL_ENERGY_BOW.get();
        ItemProperties.register(bow, new ResourceLocation("pulling"), (stack, world, user, seed) ->
            user != null && user.isUsingItem() && user.getUseItem() == stack ? 1 : 0);
        ItemProperties.register(bow, new ResourceLocation("pull"), (stack, world, user, seed) ->
            user != null && user.isUsingItem() && user.getUseItem() == stack
                ? (stack.getUseDuration() - user.getUseItemRemainingTicks()) / 20f : 0);
    }
    private SoulBowModels() {}
}
