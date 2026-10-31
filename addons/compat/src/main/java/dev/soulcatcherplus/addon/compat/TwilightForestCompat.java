// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.addon.compat;

import dev.soulcatcherplus.api.SoulAdapter;
import dev.soulcatcherplus.api.SoulAdapters;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional native master links, verified against Twilight Forest 4.3.2508 for 1.20.1. */
public final class TwilightForestCompat {
    private static final Object[] NO_ARGUMENTS = {};
    private static final ResourceLocation ICE_CRYSTAL = new ResourceLocation("twilightforest", "ice_crystal");

    public static void register() {
        SoulAdapters.register(new ResourceLocation("twilightforest", "snow_queen"), new SoulAdapter() {
            @Override public boolean inheritsTickSpawn(Mob summoner, EntityType<?> childType) {
                return ICE_CRYSTAL.equals(ForgeRegistries.ENTITY_TYPES.getKey(childType));
            }
        });
        try {
            Class<?> lich = Class.forName("twilightforest.entity.boss.Lich");
            Class<?> minion = Class.forName("twilightforest.entity.monster.LichMinion");
            MasterLink clones = new MethodLink(lich.getMethod("getMasterLich"), lich.getMethod("setMaster", lich));
            MasterLink zombies = new FieldLink(minion.getField("master"));
            SoulAdapters.register(new ResourceLocation("twilightforest", "lich"), new MasterAdapter(lich, clones));
            SoulAdapters.register(new ResourceLocation("twilightforest", "lich_minion"), new MasterAdapter(lich, zombies));
        } catch (ReflectiveOperationException | LinkageError error) {
            com.mojang.logging.LogUtils.getLogger().warn("Twilight Forest native summoner links are unavailable", error);
        }
    }

    private interface MasterLink {
        Entity get(Mob mob) throws ReflectiveOperationException;
        void set(Mob mob, Mob master) throws ReflectiveOperationException;
    }
    private record MethodLink(Method getter, Method setter) implements MasterLink {
        public Entity get(Mob mob) throws ReflectiveOperationException {
            return (Entity)getter.invoke(mob, NO_ARGUMENTS);
        }
        public void set(Mob mob, Mob master) throws ReflectiveOperationException { setter.invoke(mob, master); }
    }
    private record FieldLink(Field field) implements MasterLink {
        public Entity get(Mob mob) throws ReflectiveOperationException { return (Entity)field.get(mob); }
        public void set(Mob mob, Mob master) throws ReflectiveOperationException { field.set(mob, master); }
    }
    private record MasterAdapter(Class<?> masterType, MasterLink link) implements SoulAdapter {
        @Override public Entity summoner(Mob mob) {
            try { return link.get(mob); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to read Twilight master", error); }
        }
        @Override public void restoreSummoner(Mob minion, Mob root) {
            if (!masterType.isInstance(root)) return;
            try {
                // Native master fields are not saved. Restore the canonical link before the first AI tick.
                if (link.get(minion) != root) link.set(minion, root);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to restore Twilight master", error);
            }
        }
    }

    private TwilightForestCompat() {}
}
