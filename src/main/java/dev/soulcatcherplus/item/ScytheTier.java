// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import net.minecraft.world.item.ToolMaterial;

/** Player base attack speed is 4; iron attacks at 0.7/s, higher tiers at 0.8/s. */
public enum ScytheTier {
    IRON(ToolMaterial.IRON, 5, -3.3f, 1),
    DIAMOND(ToolMaterial.DIAMOND, 4, -3.2f, 2),
    NETHERITE(ToolMaterial.NETHERITE, 4, -3.2f, 3),
    SUPREME(ToolMaterial.NETHERITE, 5, -3.2f, 4);

    final ToolMaterial material;
    final int damageModifier;
    final float speedModifier;
    final int summonLimit;
    ScytheTier(ToolMaterial material, int damageModifier, float speedModifier, int summonLimit) {
        this.material = material; this.damageModifier = damageModifier;
        this.speedModifier = speedModifier; this.summonLimit = summonLimit;
    }
}
