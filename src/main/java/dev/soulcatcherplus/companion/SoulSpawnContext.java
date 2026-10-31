// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import dev.soulcatcherplus.api.SoulAdapters;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;

/** Construction provenance for adapter-approved synchronous summons, never a nearby-entity search. */
public final class SoulSpawnContext {
    private static final ThreadLocal<Entity> TICKING = new ThreadLocal<>();
    private static final ThreadLocal<Entity> UNLOADING = new ThreadLocal<>();
    /** Runtime state supplied by the legacy Entity lifecycle mixin; never written to NBT. */
    public interface Lifecycle {
        boolean soulcatcherplus$wasLoaded();
        boolean soulcatcherplus$wasDestroyed();
        void soulcatcherplus$markFresh();
    }
    public static boolean wasLoaded(Entity entity) { return ((Lifecycle)entity).soulcatcherplus$wasLoaded(); }
    public static boolean wasDestroyed(Entity entity) { return ((Lifecycle)entity).soulcatcherplus$wasDestroyed(); }
    public static void markFresh(Entity entity) { ((Lifecycle)entity).soulcatcherplus$markFresh(); }
    public static void loaded(Entity entity) {
        if (!entity.level.isClientSide) CREATED.remove(entity);
    }
    public static boolean unloading(Entity entity) { return UNLOADING.get() == entity; }
    /** Chunk removal uses the same native remove method as destruction on 1.16.5. */
    public static void unload(net.minecraft.world.server.ServerWorld world, Entity entity) {
        Entity previous = UNLOADING.get();
        UNLOADING.set(entity);
        try { world.onEntityRemoved(entity); }
        finally { UNLOADING.set(previous); }
    }
    // Server-thread-only. Neither a failed construction nor its creator keeps a world alive.
    private static final Map<MobEntity, WeakReference<Entity>> CREATED = new WeakHashMap<>();

    /** Called around the native server tick, including ordinary nested ticks that mask an outer soul. */
    public static void tick(Entity entity, boolean passenger) {
        Entity previous = TICKING.get();
        TICKING.set(entity);
        try {
            if (passenger) entity.rideTick();
            else entity.tick();
        } finally {
            // Keep the thread-local slot, avoiding a new ThreadLocalMap entry on every entity tick.
            TICKING.set(previous);
        }
    }

    /** Entity's base constructor is still running: only record identity, never touch MobEntity state or AI. */
    public static void constructed(Entity entity) {
        if (!(entity instanceof MobEntity mob) || entity.level.isClientSide) return;
        Entity creator = TICKING.get();
        if (creator != null && creator != entity && creator.level == entity.level
                && creator instanceof MobEntity parent && CompanionBrain.isSoul(parent)
                && CompanionBrain.activeEntry(parent) != null
                && SoulAdapters.get(parent).inheritsTickSpawn(parent, entity.getType()))
            CREATED.put(mob, new WeakReference<>(creator));
    }

    static Entity consume(MobEntity mob) {
        WeakReference<Entity> creator = CREATED.remove(mob);
        return creator == null ? null : creator.get();
    }

    private SoulSpawnContext() {}
}
