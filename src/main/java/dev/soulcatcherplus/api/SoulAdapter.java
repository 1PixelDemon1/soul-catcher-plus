// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.Entity;

/**
 * Server-side extension for an original entity type. Implementations must not
 * reference client classes. Register during FMLCommonSetupEvent.enqueueWork.
 * All callbacks run on the server thread. Never mutate the stored snapshot.
 */
public interface SoulAdapter {
    default boolean canCapture(MobEntity mob, ServerPlayerEntity player) { return true; }
    /** Whether this original mob is an automatic target for the owner's aggressive companions. */
    default boolean isHostile(MobEntity mob, ServerPlayerEntity owner) {
        return mob instanceof net.minecraft.entity.monster.IMob
            || mob.getType().getCategory() == net.minecraft.entity.EntityClassification.MONSTER
            || mob.getTarget() == owner;
    }
    default CompoundNBT capture(MobEntity mob) { return mob.saveWithoutId(new CompoundNBT()); }
    /** Stable appearance identity, excluding health, UUID, equipment and transient state. */
    default CompoundNBT variantData(CompoundNBT snapshot) {
        return dev.soulcatcherplus.data.SoulVariants.identity(snapshot);
    }
    /** Small, client-safe visual state. Never return the full captured snapshot. */
    default CompoundNBT previewData(CompoundNBT snapshot) {
        return dev.soulcatcherplus.data.SoulVariants.preview(snapshot);
    }
    /**
     * Return only entity-owned events. The default inspects direct fields heuristically;
     * override it to exclude shared encounter bars or expose events held in helpers.
     */
    default java.util.Collection<net.minecraft.world.server.ServerBossInfo> bossBars(MobEntity mob) {
        return dev.soulcatcherplus.companion.SoulBossBars.discover(mob);
    }
    /** Called after snapshot restoration, before adding the entity to the world. */
    default void prepareSummon(MobEntity mob, ServerPlayerEntity owner) {}
    /** Install movement/target overrides without removing native attack goals. */
    default void configureCompanion(MobEntity mob) {
        dev.soulcatcherplus.companion.CompanionBrain.installStandardGoals(mob);
    }
    /** Optional support for nonstandard AI; called every five server ticks. */
    default void tick(MobEntity mob, ServerPlayerEntity owner) {}
    /** Immediate creator, or null if unknown. Must not load chunks or guess from nearby mobs. */
    default Entity summoner(MobEntity mob) {
        return dev.soulcatcherplus.companion.SoulMinions.defaultSummoner(mob);
    }
    /**
     * Opt in known ownerless summons constructed during this companion's server tick.
     * Called during the child's base constructor: only its type is available.
     * Execution nesting alone is not proof of ownership; allow only known child types.
     */
    default boolean inheritsTickSpawn(MobEntity summoner, net.minecraft.entity.EntityType<?> childType) { return false; }
    /** Preserve native movement/attack goals while replacing independent victim selection. */
    default void configureMinion(MobEntity mob) { dev.soulcatcherplus.companion.LegacyGoals.clear(mob.targetSelector); }
    /** Before each native minion tick with a loaded root. Keep this cheap and idempotent; never load chunks. */
    default void restoreSummoner(MobEntity minion, MobEntity root) {
        if (minion instanceof net.minecraft.entity.monster.VexEntity vex) {
            if (vex.getOwner() != root) vex.setOwner(root);
            vex.setBoundOrigin(root.blockPosition());
        }
    }
    /** Called every five server ticks with the loaded root companion, after target inheritance. */
    default void tickMinion(MobEntity minion, MobEntity root) {}
    default void onRecall(MobEntity mob, ServerPlayerEntity owner) {}
}
