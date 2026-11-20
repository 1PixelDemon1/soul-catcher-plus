// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.api.SoulEvent;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

/** Block-bound roots reuse soul ownership, adapters, descendants and damage protection. */
public final class PylonSouls {
    public static final String PYLON = "scp_pylon";
    public static final int GUARD_RADIUS = 16, RETURN_RADIUS = 24, RESPAWN_TICKS = 40;
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public static boolean isPylon(Entity mob) { return mob.getPersistentData().getBoolean(PYLON); }

    public static PylonBond bond(Mob mob) {
        if (!isPylon(mob) || !CompanionBrain.isSoul(mob) || mob.getServer() == null
                || !mob.getPersistentData().hasUUID(CompanionBrain.ENTRY)) return null;
        PylonBond bond = SoulLedger.get(mob.getServer()).pylon(mob.getPersistentData().getUUID(CompanionBrain.ENTRY));
        return bond != null && bond.owner.equals(CompanionBrain.owner(mob))
            && bond.dimension.equals(mob.level.dimension().location().toString()) ? bond : null;
    }

    public static SoulEntry activeEntry(Mob mob) {
        return activeEntry(mob, bond(mob));
    }

    private static SoulEntry activeEntry(Mob mob, PylonBond bond) {
        if (bond == null) return null;
        UUID root = SoulMinions.isMinion(mob) ? mob.getPersistentData().getUUID(SoulMinions.ROOT) : mob.getUUID();
        return root.equals(bond.soul.activeEntity) ? bond.soul : null;
    }

    public static void creditVictory(Mob attacker, Mob victim) {
        PylonBond bond = bond(attacker);
        if (activeEntry(attacker, bond) == null || attacker.level != victim.level
                || !attacker.level.hasChunkAt(bond.position)) return;
        if (attacker.level.getBlockEntity(bond.position) instanceof SoulPylonTile pylon) {
            pylon.creditVictory(bond, SoulBalance.victoryEnergy(victim.getMaxHealth()));
        }
    }

    /** An unloaded anchor is not a broken anchor, and must not force-load a chunk. */
    public static boolean paused(Mob mob) {
        PylonBond bond = bond(mob);
        return bond != null && !mob.level.hasChunkAt(bond.position);
    }

    /** Only an active raven linked to a loaded, matching pylon may use its drop zone. */
    public static BlockPos collectorAnchor(SoulRavenEntity raven) {
        PylonBond bond = bond(raven);
        if (activeEntry(raven, bond) == null || !raven.level.hasChunkAt(bond.position)) return null;
        return raven.level.getBlockEntity(bond.position) instanceof SoulPylonTile pylon
            && pylon.matches(bond) && pylon.loaded() ? bond.position : null;
    }

    public static void tick(Mob mob) {
        PylonBond bond = bond(mob);
        if (bond == null) { mob.discard(); return; }
        if (paused(mob)) return;
        if (!(mob.level.getBlockEntity(bond.position) instanceof SoulPylonTile pylon) || !pylon.matches(bond) || !pylon.loaded()) {
            dismiss((ServerLevel) mob.level, bond);
            return;
        }
        mob.getPersistentData().putString(CompanionBrain.MODE, "GUARD");
        mob.getPersistentData().putLong("scp_guard", bond.position.above().asLong());
        SoulBossBars.suppress(mob);
        if (!(mob instanceof SoulRavenEntity)) {
            if (mob.position().distanceToSqr(Vec3.atCenterOf(bond.position)) > RETURN_RADIUS * RETURN_RADIUS) {
                mob.setTarget(null);
                moveNear(mob, bond);
            }
            CompanionBrain.guard(mob);
        }
        ServerPlayer owner = mob.getServer().getPlayerList().getPlayer(bond.owner);
        if (owner != null) {
            if (mob.tickCount % 20 == 0) SoulNames.update(mob, owner);
            SoulAdapters.get(mob).tick(mob, owner);
        }
        mob.setPersistenceRequired();
    }

    /** Confirmed destruction only; absence from a loaded-entity map is never death. */
    public static void ended(Mob mob) {
        SoulEntry entry = activeEntry(mob);
        if (entry == null) return;
        entry.activeEntity = null;
        entry.dimension = "";
        entry.readyAt = mob.getServer().overworld().getGameTime() + RESPAWN_TICKS;
        SoulLedger.get(mob.getServer()).setDirty();
        SoulMinions.dismiss(mob.getServer(), mob.getUUID());
    }

    public static boolean supportsGuard(SoulEntry entry, ServerLevel world) {
        Entity sample = null;
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entry.entityType));
            sample = type == null ? null : type.create(world);
            return sample instanceof SoulRavenEntity || sample instanceof Mob mob && SoulAdapters.get(mob).usesStandardBrain(mob);
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to inspect pylon soul {}", entry.entityType, error);
            return false;
        } finally {
            if (sample != null) sample.discard();
        }
    }

    public static boolean summon(SoulPylonTile pylon, PylonBond bond) {
        ServerLevel world = (ServerLevel) pylon.getLevel();
        // Existing public integrations require a real player for summon preparation and cancellation.
        ServerPlayer owner = world.getServer().getPlayerList().getPlayer(bond.owner);
        if (owner == null || bond.soul.activeEntity != null || !pylon.current(bond)
                || SoulCage.energy(pylon.cage()) < bond.soul.cost || !pylon.beginSummon()) return false;
        Entity created = null;
        UUID planned = null;
        boolean completed = false;
        ItemStack paymentCage = pylon.cage();
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(bond.soul.entityType));
            created = type == null ? null : type.create(world);
            if (!(created instanceof Mob mob)) return false;
            mob.load(SoulSnapshots.sanitize(bond.soul.snapshot()));
            SoulSpawnContext.markFresh(mob);
            mob.setUUID(UUID.randomUUID());
            mob.setHealth(mob.getMaxHealth());
            mob.deathTime = 0;
            mob.setPersistenceRequired();
            mob.setCanPickUpLoot(false);
            mob.getPersistentData().putUUID(CompanionBrain.OWNER, bond.owner);
            mob.getPersistentData().putUUID(CompanionBrain.ENTRY, bond.soul.id);
            mob.getPersistentData().putBoolean(PYLON, true);
            mob.getPersistentData().putString(CompanionBrain.MODE, "GUARD");
            mob.getPersistentData().putLong("scp_guard", bond.position.above().asLong());
            // Preparation may itself create children; their ownership lookup must already be valid.
            planned = mob.getUUID();
            bond.soul.activeEntity = planned;
            bond.soul.dimension = bond.dimension;
            SoulLedger.get(world.getServer()).setDirty();
            SoulAdapters.get(mob).prepareSummon(mob, owner);
            SoulAdapters.get(mob).configureCompanion(mob);
            SoulBossBars.suppress(mob);
            if (!moveNear(mob, bond) || MinecraftForge.EVENT_BUS.post(new SoulEvent.Summon(owner, mob))) return false;
            // Callbacks may remove the block or change its contents. Revalidate before charging.
            if (!pylon.current(bond) || !pylon.loaded()
                    || SoulCage.energy(pylon.cage()) < bond.soul.cost) return false;
            SoulNames.update(mob, owner);
            if (!world.addFreshEntity(mob)) return false;
            // Level-join callbacks may cancel, destroy the pylon or replace its reservation.
            // No energy has changed yet, so a failed provisional spawn needs no item refund.
            if (!mob.isAlive() || mob.isRemoved() || !pylon.current(bond) || !pylon.loaded()
                    || !mob.getUUID().equals(bond.soul.activeEntity) || pylon.cage() != paymentCage
                    || SoulCage.energy(paymentCage) < bond.soul.cost) return false;
            SoulCage.setEnergy(paymentCage, SoulCage.energy(paymentCage) - bond.soul.cost);
            completed = true;
            return true;
        } catch (RuntimeException error) {
            LOGGER.warn("Unable to summon pylon guardian {}", bond.soul.entityType, error);
            return false;
        } finally {
            if (!completed) {
                if (planned != null && planned.equals(bond.soul.activeEntity)) {
                    bond.soul.activeEntity = null;
                    bond.soul.dimension = "";
                }
                if (created != null) {
                    SoulMinions.dismiss(world.getServer(), created.getUUID());
                    created.discard();
                }
            }
            pylon.endSummon();
        }
    }

    private static boolean moveNear(Mob mob, PylonBond bond) {
        Vec3 center = Vec3.atCenterOf(bond.position).add(0, .5, 0);
        Vec3 destination = SoulPlacement.findNear(mob, null, center);
        if (destination == null) return false;
        mob.stopRiding();
        if (mob.isPassenger()) return false;
        mob.moveTo(destination.x, destination.y, destination.z, mob.getYRot(), 0);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;
        return true;
    }

    public static void dismiss(ServerLevel world, PylonBond bond) {
        // Invalidating first makes reentrant callbacks and unloaded descendants harmless.
        SoulLedger.get(world.getServer()).removePylon(bond.soul.id);
        UUID active = bond.soul.activeEntity;
        bond.soul.activeEntity = null;
        if (active == null) return;
        Entity entity = world.getEntity(active);
        if (entity instanceof Mob mob && isPylon(mob)
                && bond.owner.equals(CompanionBrain.owner(mob))) {
            ServerPlayer owner = world.getServer().getPlayerList().getPlayer(bond.owner);
            try { if (owner != null) SoulAdapters.get(mob).onRecall(mob, owner); }
            catch (RuntimeException error) { LOGGER.warn("Pylon guardian cleanup failed", error); }
            SoulNetwork.dissolve(mob);
            mob.discard();
        }
        SoulMinions.dismiss(world.getServer(), active);
    }
    private PylonSouls() {}
}
