// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import com.google.gson.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulProfiles extends SimpleJsonResourceReloadListener<JsonElement> {
    public record Profile(boolean enabled, int cost, int energy) {}
    private static Map<Identifier, Boolean> enabledTypes = com.google.common.collect.ImmutableMap.of();
    public SoulProfiles() { super(com.mojang.serialization.Codec.PASSTHROUGH.xmap(value -> value.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue(), value -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,value)), net.minecraft.resources.FileToIdConverter.json("soul_profiles")); }
    public static Profile get(Mob mob) {
        Identifier type = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return new Profile(enabledTypes.getOrDefault(type, true),
            SoulBalance.summonCost(mob.getMaxHealth()), SoulBalance.victoryEnergy(mob.getMaxHealth()));
    }
    @Override protected void apply(Map<Identifier, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, Boolean> loaded = new HashMap<>();
        json.forEach((id, element) -> {
            JsonObject obj = element.getAsJsonObject();
            Identifier type = Identifier.parse(obj.get("entity").getAsString());
            loaded.put(type, !obj.has("enabled") || obj.get("enabled").getAsBoolean());
        });
        enabledTypes = com.google.common.collect.ImmutableMap.copyOf(loaded);
    }
}
