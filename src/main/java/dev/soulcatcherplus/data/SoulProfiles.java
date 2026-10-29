// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import com.google.gson.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SoulProfiles extends SimpleJsonResourceReloadListener {
    public record Profile(boolean enabled, int cost, int energy) {}
    private static Map<ResourceLocation, Boolean> enabledTypes = Map.of();
    public SoulProfiles() { super(new Gson(), "soul_profiles"); }
    public static Profile get(Mob mob) {
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return new Profile(enabledTypes.getOrDefault(type, true),
            SoulBalance.summonCost(mob.getMaxHealth()), SoulBalance.victoryEnergy(mob.getMaxHealth()));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Boolean> loaded = new HashMap<>();
        json.forEach((id, element) -> {
            JsonObject obj = element.getAsJsonObject();
            ResourceLocation type = new ResourceLocation(obj.get("entity").getAsString());
            loaded.put(type, !obj.has("enabled") || obj.get("enabled").getAsBoolean());
        });
        enabledTypes = Map.copyOf(loaded);
    }
}
