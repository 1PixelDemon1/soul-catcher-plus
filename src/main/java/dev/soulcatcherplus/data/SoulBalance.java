// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;

/** Shared integer economy: round costs upward and energy returns downward. */
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
    public static int recallEnergy(double currentHealth) {
        if (!Double.isFinite(currentHealth) || currentHealth <= 0) return 0;
        return (int)Math.floor(currentHealth / 2);
    }

    /** Best-effort migration for old entries that stored attributes but no max-health field. */
    public static double legacyHealth(CompoundNBT snapshot, int oldCost) {
        for (INBT raw : snapshot.getList("Attributes", 10)) {
            CompoundNBT attribute = (CompoundNBT) raw;
            String name = attribute.getString("Name");
            if (!name.equals("minecraft:generic.max_health") && !name.equals("generic.max_health")) continue;
            double base = attribute.getDouble("Base"), add = 0, multiplyBase = 0, totalFactor = 1;
            for (INBT modifierRaw : attribute.getList("Modifiers", 10)) {
                CompoundNBT modifier = (CompoundNBT) modifierRaw;
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
