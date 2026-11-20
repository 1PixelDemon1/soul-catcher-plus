// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity.ai;

import net.minecraft.world.phys.AABB;

import net.minecraft.core.BlockPos;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.entity.RavenItemDelivery;
import dev.soulcatcherplus.pylon.PylonSouls;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Bounded batches of real pickups; all searches stay in loaded chunks. */
public final class SoulRavenCollectGoal extends Goal {
    private static final double SEARCH_RANGE = 20.0;
    private static final double DELIVERY_RANGE = 2.5;
    private static final double BATCH_RANGE = 2.5, RETURN_SPEED = 1.15, FETCH_SPEED = 3.2, APPROACH_SPEED = 1.7;
    private static final int SEARCH_INTERVAL = 20, RETRY_DELAY = 100, TRIP_TIMEOUT = 200;
    private static final int SWEEP_INTERVAL = 5;
    private final SoulRavenEntity raven;
    private final Map<UUID, Long> ignoredUntil = new HashMap<>();
    private ServerPlayer owner;
    private BlockPos pylon;
    private Entity target;
    private long nextSearch, tripDeadline, returnDeadline, nextPath, nextSweep;

    public SoulRavenCollectGoal(SoulRavenEntity raven) {
        this.raven = raven;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        if (PylonSouls.isPylon(raven)) {
            pylon = PylonSouls.collectorAnchor(raven);
            return pylon != null;
        }
        owner = CompanionBrain.isSoul(raven) && raven.getServer() != null
            ? raven.getServer().getPlayerList().getPlayer(CompanionBrain.owner(raven)) : null;
        return owner != null && owner.isAlive() && owner.level() == raven.level()
            && CompanionBrain.activeEntry(raven) != null;
    }

    @Override public boolean canContinueToUse() { return canUse(); }

    @Override public void start() { returnDeadline = raven.level().getGameTime() + TRIP_TIMEOUT; }

    @Override public void stop() {
        target = null;
        raven.setPerched(false);
        raven.getNavigation().stop();
        // A raven briefly separated from its unloaded pylon keeps the real cargo.
        // Death, removal and dismantling use the entity's normal dismount path.
        if (!PylonSouls.isPylon(raven)) raven.releaseCargo();
    }

    @Override public void tick() {
        long now = raven.level().getGameTime();
        Vec3 anchor = pylon == null ? owner.position() : Vec3.atCenterOf(pylon);
        Vec3 home = pylon == null ? SoulRavenEntity.shoulderPosition(owner, 1) : anchor.add(0, 1.2, 0);
        if (raven.isPerched() || target != null) returnDeadline = now + TRIP_TIMEOUT;
        if (raven.position().distanceToSqr(anchor) > 32 * 32 || now >= returnDeadline) {
            target = null;
            returnDeadline = now + TRIP_TIMEOUT;
            raven.getNavigation().stop();
            if (pylon == null || canRestAt(home)) raven.teleportTo(home.x, home.y, home.z);
        }
        if (raven.hasCargo()) {
            target = null;
            if (raven.position().distanceToSqr(anchor) <= DELIVERY_RANGE * DELIVERY_RANGE) {
                if (pylon == null) deliver(now);
                else {
                    Vec3 drop = RavenPylonDrop.find(raven.level(), pylon);
                    if (drop != null) deliverToPylon(drop, now);
                    restNearPylon(home, now);
                }
            }
            else {
                collectNearby(now);
                Vec3 destination = pylon == null ? anchor.add(0, 1.3, 0) : home;
                flyTo(destination.x, destination.y, destination.z, RETURN_SPEED, now);
            }
            return;
        }
        if (target != null) {
            if (!available(target, now) || now >= tripDeadline) {
                ignore(target, now);
                target = null;
            } else {
                if (raven.distanceToSqr(target) <= 1.4 * 1.4 && raven.hasLineOfSight(target)) {
                    if (!raven.takeCargo(target)) ignore(target, now);
                    target = null;
                    nextPath = 0;
                    nextSweep = 0;
                    collectNearby(now);
                } else {
                    double speed = raven.distanceToSqr(target) < 4 * 4 ? APPROACH_SPEED : FETCH_SPEED;
                    flyTo(target.getX(), target.getY() + 0.7, target.getZ(), speed, now);
                }
                return;
            }
        }
        if (now >= nextSearch && (pylon != null || !owner.isSpectator())) {
            nextSearch = now + SEARCH_INTERVAL;
            ignoredUntil.values().removeIf(until -> until <= now);
            AABB search = pylon == null ? owner.getBoundingBox() : new AABB(pylon);
            if (pylon == null || RavenPylonDrop.find(raven.level(), pylon) != null) {
                Class<? extends Entity> pickupType = pylon == null ? Entity.class : ItemEntity.class;
                target = raven.level().getEntitiesOfClass(pickupType, search.inflate(SEARCH_RANGE), entity -> available(entity, now))
                    .stream().min(java.util.Comparator.comparingDouble(raven::distanceToSqr)).orElse(null);
            }
            if (target != null) {
                tripDeadline = now + TRIP_TIMEOUT;
                nextPath = 0;
                raven.setPerched(false);
                return;
            }
        }
        if (pylon != null) restNearPylon(home, now);
        else if (raven.position().distanceToSqr(home) < 1.2 * 1.2) raven.setPerched(true);
        else flyTo(home.x, home.y, home.z, RETURN_SPEED, now);
    }

    private boolean available(Entity entity, long now) {
        if (!(entity instanceof ItemEntity || entity instanceof SoulRemnant) || !entity.isAlive()
                || entity.isPassenger() || entity.level() != raven.level()
                || ignoredUntil.getOrDefault(entity.getUUID(), 0L) > now) return false;
        if (pylon != null) {
            return entity instanceof ItemEntity item && pickupReady(item, CompanionBrain.owner(raven))
                && item.position().distanceToSqr(Vec3.atCenterOf(pylon)) <= SEARCH_RANGE * SEARCH_RANGE
                && !RavenPylonDrop.inStockpile(pylon, item.blockPosition());
        }
        if (entity.distanceToSqr(owner) > SEARCH_RANGE * SEARCH_RANGE) return false;
        if (entity instanceof SoulRemnant remnant) return remnant.canCollect(owner);
        ItemEntity item = (ItemEntity) entity;
        return !item.hasPickUpDelay() && !item.getItem().isEmpty()
            && (RavenItemDelivery.pickupTarget(item) == null || RavenItemDelivery.pickupTarget(item).equals(owner.getUUID()))
            // Delivered overflow has already reached its destination, even with a full inventory.
            // If the owner moves away, the raven can bring that loot to the new position again.
            && !(RavenItemDelivery.isProtected(item) && item.distanceToSqr(owner) <= DELIVERY_RANGE * DELIVERY_RANGE);
    }

    private static boolean pickupReady(ItemEntity item, UUID owner) {
        return !item.hasPickUpDelay() && !item.getItem().isEmpty() && (item.getOwner() == null || item.getOwner().equals(owner));
    }

    private void collectNearby(long now) {
        if (!raven.canCarryMore() || now < nextSweep || (pylon == null && owner.isSpectator())) return;
        nextSweep = now + SWEEP_INTERVAL;
        for (Entity pickup : raven.level().getEntitiesOfClass(Entity.class, raven.getBoundingBox().inflate(BATCH_RANGE),
                entity -> available(entity, now) && raven.distanceToSqr(entity) <= BATCH_RANGE * BATCH_RANGE
                    && raven.hasLineOfSight(entity))) {
            if (!raven.canCarryMore()) break;
            if (!raven.takeCargo(pickup)) ignore(pickup, now);
        }
    }

    private void deliver(long now) {
        for (Entity cargo : new ArrayList<>(raven.getPassengers())) {
            if (!raven.releaseCargo(cargo) || !cargo.isAlive()) continue;
            cargo.setPos(owner.getX(), owner.getY() + 0.5, owner.getZ());
            if (cargo instanceof ItemEntity item) {
                RavenItemDelivery.deliver(item, owner);
            } else if (cargo instanceof SoulRemnant remnant) remnant.collect(owner);
            if (cargo.isAlive()) ignore(cargo, now);
        }
        nextSearch = now + 10;
    }

    private void deliverToPylon(Vec3 drop, long now) {
        for (Entity cargo : new ArrayList<>(raven.getPassengers())) {
            if (!raven.releaseCargo(cargo) || !cargo.isAlive()) continue;
            cargo.setPos(drop.x, drop.y, drop.z);
            ignore(cargo, now);
        }
        nextSearch = now + 10;
    }

    private boolean canRestAt(Vec3 home) {
        return raven.level().noCollision(raven, raven.getBoundingBox().move(home.subtract(raven.position())));
    }

    private void restNearPylon(Vec3 home, long now) {
        if (raven.isPerched()) return;
        if (raven.position().distanceToSqr(home) < 1.2 * 1.2 && canRestAt(home)) {
            raven.setPos(home.x, home.y, home.z);
            raven.setPerched(true);
        } else flyTo(home.x, home.y, home.z, RETURN_SPEED, now);
    }

    private void flyTo(double x, double y, double z, double speed, long now) {
        raven.setPerched(false);
        raven.getNavigation().setSpeedModifier(speed);
        if (now >= nextPath) {
            nextPath = now + 10;
            raven.getNavigation().moveTo(x, y, z, speed);
        }
        // Near a floating pickup/shoulder there may be no navigable endpoint block.
        if (raven.getNavigation().isDone()) raven.getMoveControl().setWantedPosition(x, y, z, speed);
        raven.getLookControl().setLookAt(x, y, z);
    }

    private void ignore(Entity entity, long now) {
        // Bound even a dense or adversarial pile of uncollectable pickups.
        if (ignoredUntil.size() >= 32) ignoredUntil.clear();
        ignoredUntil.put(entity.getUUID(), now + RETRY_DELAY);
    }
}
