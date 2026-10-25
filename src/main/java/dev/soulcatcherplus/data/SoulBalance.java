// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Shared integer economy: charge upward, reward downward, never below one. */
public final class SoulBalance {
    public static double health(double maxHealth) {
        return Double.isFinite(maxHealth) && maxHealth > 0 ? maxHealth : 1;
    }
    public static int summonCost(double maxHealth) {
        return Math.max(1, (int)Math.ceil(health(maxHealth) * 2));
    }
    public static int victoryEnergy(double maxHealth) {
        return Math.max(1, (int)Math.floor(health(maxHealth) / 2));
    }

    /** Best-effort migration for old entries that stored attributes but no max-health field. */
    public static double legacyHealth(CompoundTag snapshot, int oldCost) {
        for (Tag raw : snapshot.getList("Attributes", Tag.TAG_COMPOUND)) {
            CompoundTag attribute = (CompoundTag) raw;
            String name = attribute.getString("Name");
            if (!name.equals("minecraft:generic.max_health") && !name.equals("generic.max_health")) continue;
            double base = attribute.getDouble("Base"), add = 0, multiplyBase = 0, totalFactor = 1;
            for (Tag modifierRaw : attribute.getList("Modifiers", Tag.TAG_COMPOUND)) {
                CompoundTag modifier = (CompoundTag) modifierRaw;
                double amount = modifier.getDouble("Amount");
                switch (modifier.getInt("Operation")) {
                    case 0 -> add += amount;
                    case 1 -> multiplyBase += amount;
                    case 2 -> totalFactor *= 1 + amount;
                    default -> { }
                }
            }
            return health((base + add) * (1 + multiplyBase) * totalFactor);
        }
        return health(oldCost / 2d);
    }
    private SoulBalance() {}
}
