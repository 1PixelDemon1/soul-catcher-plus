// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;

/**
 * Server-side extension for an original entity type. Implementations must not
 * reference client classes. Register during FMLCommonSetupEvent.enqueueWork.
 * All callbacks run on the server thread. Never mutate the stored snapshot.
 */
public interface SoulAdapter {
    default boolean canCapture(Mob mob, ServerPlayer player) { return true; }
    /** Whether this original mob is an automatic target for the owner's aggressive companions. */
    default boolean isHostile(Mob mob, ServerPlayer owner) {
        return mob instanceof net.minecraft.world.entity.monster.Enemy
            || mob.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
            || mob.getTarget() == owner;
    }
    default CompoundTag capture(Mob mob) { return mob.saveWithoutId(new CompoundTag()); }
    /** Stable appearance identity, excluding health, UUID, equipment and transient state. */
    default CompoundTag variantData(CompoundTag snapshot) {
        return dev.soulcatcherplus.data.SoulVariants.identity(snapshot);
    }
    /** Small, client-safe visual state. Never return the full captured snapshot. */
    default CompoundTag previewData(CompoundTag snapshot) {
        return dev.soulcatcherplus.data.SoulVariants.preview(snapshot);
    }
    /**
     * Return only entity-owned events. The default inspects direct fields heuristically;
     * override it to exclude shared encounter bars or expose events held in helpers.
     */
    default java.util.Collection<net.minecraft.server.level.ServerBossEvent> bossBars(Mob mob) {
        return dev.soulcatcherplus.companion.SoulBossBars.discover(mob);
    }
    /** Called after snapshot restoration, before adding the entity to the world. */
    default void prepareSummon(Mob mob, ServerPlayer owner) {}
    /** Install movement/target overrides without removing native attack goals. */
    default void configureCompanion(Mob mob) {
        dev.soulcatcherplus.companion.CompanionBrain.installStandardGoals(mob);
    }
    /** Optional support for nonstandard AI; called every five server ticks. */
    default void tick(Mob mob, ServerPlayer owner) {}
    /** Immediate creator, or null if unknown. Must not load chunks or guess from nearby mobs. */
    default Entity summoner(Mob mob) {
        return dev.soulcatcherplus.companion.SoulMinions.defaultSummoner(mob);
    }
    /**
     * Opt in known ownerless summons constructed during this companion's server tick.
     * Called during the child's base constructor: only its type is available.
     * Execution nesting alone is not proof of ownership; allow only known child types.
     */
    default boolean inheritsTickSpawn(Mob summoner, net.minecraft.world.entity.EntityType<?> childType) { return false; }
    /** Preserve native movement/attack goals while replacing independent victim selection. */
    default void configureMinion(Mob mob) { mob.targetSelector.removeAllGoals(goal -> true); }
    /** Before each native minion tick with a loaded root. Keep this cheap and idempotent; never load chunks. */
    default void restoreSummoner(Mob minion, Mob root) {
        if (minion instanceof net.minecraft.world.entity.monster.Vex vex) {
            if (vex.getOwner() != root) vex.setOwner(root);
            vex.setBoundOrigin(root.blockPosition());
        }
    }
    /** Called every five server ticks with the loaded root companion, after target inheritance. */
    default void tickMinion(Mob minion, Mob root) {}
    default void onRecall(Mob mob, ServerPlayer owner) {}
}
