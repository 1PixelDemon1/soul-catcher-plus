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
        if (pauseWithoutOwner(entity, passenger)) return;
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

    /** Pause outside the entire native tick: custom AI, fuses and lifetime timers
     * may run before/after LivingUpdateEvent. Passengers are ticked separately. */
    private static boolean pauseWithoutOwner(Entity entity, boolean passenger) {
        if (entity instanceof net.minecraft.entity.player.PlayerEntity || !entity.isAlive()) return false;
        Entity source = entity;
        while (source != null && !(source instanceof MobEntity && CompanionBrain.isSoul(source)))
            source = passenger ? source.getVehicle() : null;
        if (!(source instanceof MobEntity mob) || !mob.isAlive() || !CompanionBrain.isSoul(mob) || mob.getServer() == null
                || mob.getServer().getPlayerList().getPlayer(CompanionBrain.owner(mob)) != null
                // Invalid reservations still reach normal stale-entity/cargo cleanup.
                || CompanionBrain.activeEntry(mob) == null) return false;
        if (entity == source) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            CompanionBrain.resetReturn(mob);
        }
        entity.setDeltaMovement(net.minecraft.util.math.vector.Vector3d.ZERO);
        return true;
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
