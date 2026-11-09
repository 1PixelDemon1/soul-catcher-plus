// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Shared integer economy: round costs upward and energy returns downward. */
public final class SoulBalance {
    /** Shared visual grade for skull remnants and highlighted collection cards. */
    public static boolean isBossSoul(double maxHealth) { return health(maxHealth) >= 150; }
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
    public static double legacyHealth(CompoundTag snapshot, int oldCost) {
        for (Tag raw : snapshot.getListOrEmpty("Attributes")) {
            CompoundTag attribute = (CompoundTag) raw;
            String name = attribute.getStringOr("Name", "");
            if (!name.equals("minecraft:generic.max_health") && !name.equals("generic.max_health")) continue;
            double base = attribute.getDoubleOr("Base", 0d), add = 0, multiplyBase = 0, totalFactor = 1;
            for (Tag modifierRaw : attribute.getListOrEmpty("Modifiers")) {
                CompoundTag modifier = (CompoundTag) modifierRaw;
                double amount = modifier.getDoubleOr("Amount", 0d);
                switch (modifier.getIntOr("Operation", 0)) {
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
