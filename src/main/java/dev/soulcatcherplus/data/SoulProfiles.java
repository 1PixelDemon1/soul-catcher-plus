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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulProfiles extends SimpleJsonResourceReloadListener {
    public record Profile(boolean enabled, int cost, int slots, int energy) {}
    private static Map<ResourceLocation, Profile> profiles = Map.of();
    public SoulProfiles() { super(new Gson(), "soul_profiles"); }
    public static Profile get(Mob mob) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        double attack = mob.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 2 : mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        int cost = Math.max(10, (int) Math.ceil(mob.getMaxHealth() + attack * 2));
        return profiles.getOrDefault(type, new Profile(true, cost, 1, Math.max(5, cost / 4)));
    }
    @Override protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Profile> loaded = new HashMap<>();
        json.forEach((id, element) -> {
            JsonObject obj = element.getAsJsonObject();
            ResourceLocation type = new ResourceLocation(obj.get("entity").getAsString());
            Profile profile = new Profile(!obj.has("enabled") || obj.get("enabled").getAsBoolean(),
                Math.max(1, obj.get("cost").getAsInt()), Math.max(1, obj.get("slots").getAsInt()),
                Math.max(0, obj.get("energy").getAsInt()));
            loaded.put(type, profile);
        });
        profiles = Map.copyOf(loaded);
    }
}
