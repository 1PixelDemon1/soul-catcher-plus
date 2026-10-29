// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import dev.soulcatcherplus.api.SoulAdapters;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Construction provenance for adapter-approved synchronous summons, never a nearby-entity search. */
public final class SoulSpawnContext {
    private static final ThreadLocal<Entity> TICKING = new ThreadLocal<>();
    // Server-thread-only. Neither a failed construction nor its creator keeps a world alive.
    private static final Map<Mob, WeakReference<Entity>> CREATED = new WeakHashMap<>();

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

    /** Entity's base constructor is still running: only record identity, never touch Mob state or AI. */
    public static void constructed(Entity entity) {
        if (!(entity instanceof Mob mob) || entity.level().isClientSide) return;
        Entity creator = TICKING.get();
        if (creator != null && creator != entity && creator.level() == entity.level()
                && creator instanceof Mob parent && CompanionBrain.isSoul(parent)
                && CompanionBrain.activeEntry(parent) != null
                && SoulAdapters.get(parent).inheritsTickSpawn(parent, entity.getType()))
            CREATED.put(mob, new WeakReference<>(creator));
    }

    static Entity consume(Mob mob) {
        WeakReference<Entity> creator = CREATED.remove(mob);
        return creator == null ? null : creator.get();
    }

    private SoulSpawnContext() {}
}
