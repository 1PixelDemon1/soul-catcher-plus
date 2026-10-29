// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Explicit adapters take priority over the standard goal-based implementation. */
public final class SoulAdapters {
    private static final Map<ResourceLocation, SoulAdapter> ADAPTERS = new HashMap<>();
    private static final SoulAdapter STANDARD = new SoulAdapter() {};
    public static void register(ResourceLocation entityType, SoulAdapter adapter) {
        if (ADAPTERS.putIfAbsent(java.util.Objects.requireNonNull(entityType),
                java.util.Objects.requireNonNull(adapter)) != null)
            throw new IllegalArgumentException("Duplicate soul adapter: " + entityType);
    }
    public static SoulAdapter get(MobEntity mob) {
        return get(ForgeRegistries.ENTITIES.getKey(mob.getType()));
    }
    public static SoulAdapter get(ResourceLocation entityType) { return ADAPTERS.getOrDefault(entityType, STANDARD); }
    private SoulAdapters() {}
}
