// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import net.minecraft.world.item.Tiers;

/** Player base attack speed is 4; modifiers produce 0.7/0.8/0.8 attacks per second. */
public enum ScytheTier {
    IRON(Tiers.IRON, 5, -3.3f, 1),
    DIAMOND(Tiers.DIAMOND, 4, -3.2f, 2),
    NETHERITE(Tiers.NETHERITE, 4, -3.2f, 3);

    final Tiers material;
    final int damageModifier;
    final float speedModifier;
    final int summonLimit;
    ScytheTier(Tiers material, int damageModifier, float speedModifier, int summonLimit) {
        this.material = material; this.damageModifier = damageModifier;
        this.speedModifier = speedModifier; this.summonLimit = summonLimit;
    }
}
