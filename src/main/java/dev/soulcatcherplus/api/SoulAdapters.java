// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit adapters take priority over the standard goal-based implementation. */
public final class SoulAdapters {
    private static final Map<Identifier, SoulAdapter> ADAPTERS = new HashMap<>();
    private static final SoulAdapter STANDARD = new SoulAdapter() {};
    public static void register(Identifier entityType, SoulAdapter adapter) {
        if (ADAPTERS.putIfAbsent(java.util.Objects.requireNonNull(entityType),
                java.util.Objects.requireNonNull(adapter)) != null)
            throw new IllegalArgumentException("Duplicate soul adapter: " + entityType);
    }
    public static SoulAdapter get(Mob mob) {
        return get(ForgeRegistries.ENTITY_TYPES.getKey(mob.getType()));
    }
    public static SoulAdapter get(Identifier entityType) { return ADAPTERS.getOrDefault(entityType, STANDARD); }
    private SoulAdapters() {}
}
