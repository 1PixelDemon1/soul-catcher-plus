// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.CompanionBrain;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attributes music to its entity without muting the whole music category or a mod's soundtrack. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class SoulMusic {
    private static final ThreadLocal<Entity> TICKING = new ThreadLocal<>();
    private static final List<Function<SoundInstance, Entity>> SOURCES = new ArrayList<>();
    private static final Map<SoundInstance, WeakReference<Entity>> PLAYING = new IdentityHashMap<>();
    private static ClientLevel world;
    private static final ClassValue<List<Field>> ENTITY_FIELDS = new ClassValue<List<Field>>() {
        @Override protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !Entity.class.isAssignableFrom(field.getType())) continue;
                    try { field.setAccessible(true); fields.add(field); }
                    catch (RuntimeException ignored) { /* Inaccessible fields do not prove ownership. */ }
                }
            }
            return Collections.unmodifiableList(fields);
        }
    };

    /** Register on the client thread during client setup; return null for unsupported sound instances. */
    public static void registerSource(Function<SoundInstance, Entity> source) { SOURCES.add(Objects.requireNonNull(source)); }
    public static Entity currentEntity() { return TICKING.get(); }

    /** Includes passengers; finally restores the previous source even when a native tick fails. */
    public static void tickEntity(Entity entity, boolean passenger) {
        Entity previous = TICKING.get();
        TICKING.set(entity);
        try {
            if (passenger) entity.rideTick();
            else entity.tick();
        } finally {
            if (previous == null) TICKING.remove();
            else TICKING.set(previous);
        }
    }
    public static boolean suppressCurrentEntity() {
        Entity entity = currentEntity();
        return entity != null && CompanionBrain.isSoul(entity);
    }
    private static Entity source(SoundInstance sound) {
        for (Function<SoundInstance, Entity> resolver : SOURCES) {
            Entity entity = resolver.apply(sound);
            if (entity != null) return entity;
        }
        // A single direct entity reference is unambiguous. Multiple references need an explicit resolver.
        List<Field> fields = ENTITY_FIELDS.get(sound.getClass());
        if (fields.size() == 1) {
            try {
                Entity entity = (Entity)fields.get(0).get(sound);
                if (entity != null) return entity;
            } catch (IllegalAccessException ignored) { }
        }
        return TICKING.get();
    }
    private static void updateWorld(Minecraft client) {
        if (world == client.level) return;
        PLAYING.clear();
        world = client.level;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void play(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() != SoundSource.MUSIC) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        updateWorld(client);
        if (world == null) return;
        Entity source = source(sound);
        if (source == null || source.level() != world) return;
        if (CompanionBrain.isSoul(source)) event.setSound(null);
        else PLAYING.put(sound, new WeakReference<>(source));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        updateWorld(client);
        // Keep only active music, not every world entity. This also handles a late ownership packet.
        Iterator<Map.Entry<SoundInstance, WeakReference<Entity>>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SoundInstance, WeakReference<Entity>> entry = iterator.next();
            Entity entity = entry.getValue().get();
            // Shared sound objects may be reassigned to a different boss by their native controller.
            Entity currentSource = source(entry.getKey());
            if (currentSource != null && currentSource != entity) {
                entity = currentSource;
                entry.setValue(new WeakReference<>(entity));
            }
            if (entity != null && CompanionBrain.isSoul(entity)) {
                client.getSoundManager().stop(entry.getKey());
                iterator.remove();
            } else if (entity == null || !client.getSoundManager().isActive(entry.getKey())) iterator.remove();
        }
    }
    private SoulMusic() {}
}
