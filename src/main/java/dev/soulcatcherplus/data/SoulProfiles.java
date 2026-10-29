// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import com.google.gson.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ResourceLocation;
import net.minecraft.resources.IResourceManager;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.profiler.IProfiler;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulProfiles extends JsonReloadListener {
    @com.github.bsideup.jabel.Desugar
    public record Profile(boolean enabled, int cost, int energy) {}
    private static Map<ResourceLocation, Boolean> enabledTypes = com.google.common.collect.ImmutableMap.of();
    public SoulProfiles() { super(new Gson(), "soul_profiles"); }
    public static Profile get(MobEntity mob) {
        ResourceLocation type = ForgeRegistries.ENTITIES.getKey(mob.getType());
        return new Profile(enabledTypes.getOrDefault(type, true),
            SoulBalance.summonCost(mob.getMaxHealth()), SoulBalance.victoryEnergy(mob.getMaxHealth()));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, IResourceManager manager, IProfiler profiler) {
        Map<ResourceLocation, Boolean> loaded = new HashMap<>();
        json.forEach((id, element) -> {
            JsonObject obj = element.getAsJsonObject();
            ResourceLocation type = new ResourceLocation(obj.get("entity").getAsString());
            loaded.put(type, !obj.has("enabled") || obj.get("enabled").getAsBoolean());
        });
        enabledTypes = com.google.common.collect.ImmutableMap.copyOf(loaded);
    }
}
