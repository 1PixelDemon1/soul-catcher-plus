// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.addon.compat;

import dev.soulcatcherplus.api.SoulAdapter;
import dev.soulcatcherplus.api.SoulAdapters;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Optional native master links, verified against Twilight Forest 4.0.870 for 1.16.5. */
public final class TwilightForestCompat {
    private static final Object[] NO_ARGUMENTS = {};
    private static final ResourceLocation ICE_CRYSTAL = new ResourceLocation("twilightforest", "ice_crystal");

    public static void register() {
        SoulAdapters.register(new ResourceLocation("twilightforest", "snow_queen"), new SoulAdapter() {
            @Override public boolean inheritsTickSpawn(MobEntity summoner, EntityType<?> childType) {
                return ICE_CRYSTAL.equals(ForgeRegistries.ENTITIES.getKey(childType));
            }
        });
        try {
            Class<?> lich = Class.forName("twilightforest.entity.boss.LichEntity");
            Class<?> minion = Class.forName("twilightforest.entity.boss.LichMinionEntity");
            MasterLink clones = new MethodLink(lich.getMethod("getMasterLich"), lich.getMethod("setMaster", lich));
            Field master = minion.getDeclaredField("master");
            master.setAccessible(true);
            MasterLink zombies = new FieldLink(master);
            SoulAdapters.register(new ResourceLocation("twilightforest", "lich"), new MasterAdapter(lich, clones));
            SoulAdapters.register(new ResourceLocation("twilightforest", "lich_minion"), new MasterAdapter(lich, zombies));
        } catch (ReflectiveOperationException | LinkageError error) {
            org.apache.logging.log4j.LogManager.getLogger().warn("Twilight Forest native summoner links are unavailable", error);
        }
    }

    private interface MasterLink {
        Entity get(MobEntity mob) throws ReflectiveOperationException;
        void set(MobEntity mob, MobEntity master) throws ReflectiveOperationException;
    }
    @com.github.bsideup.jabel.Desugar
    private record MethodLink(Method getter, Method setter) implements MasterLink {
        public Entity get(MobEntity mob) throws ReflectiveOperationException {
            return (Entity)getter.invoke(mob, NO_ARGUMENTS);
        }
        public void set(MobEntity mob, MobEntity master) throws ReflectiveOperationException { setter.invoke(mob, master); }
    }
    @com.github.bsideup.jabel.Desugar
    private record FieldLink(Field field) implements MasterLink {
        public Entity get(MobEntity mob) throws ReflectiveOperationException { return (Entity)field.get(mob); }
        public void set(MobEntity mob, MobEntity master) throws ReflectiveOperationException { field.set(mob, master); }
    }
    @com.github.bsideup.jabel.Desugar
    private record MasterAdapter(Class<?> masterType, MasterLink link) implements SoulAdapter {
        @Override public Entity summoner(MobEntity mob) {
            try { return link.get(mob); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unable to read Twilight master", error); }
        }
        @Override public void restoreSummoner(MobEntity minion, MobEntity root) {
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
