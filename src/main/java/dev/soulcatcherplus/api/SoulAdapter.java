// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Server-side extension for an original entity type. Implementations must not
 * reference client classes. Register during FMLCommonSetupEvent.enqueueWork.
 * All callbacks run on the server thread. Never mutate the stored snapshot.
 */
public interface SoulAdapter {
    default boolean canCapture(Mob mob, ServerPlayer player) { return true; }
    default CompoundTag capture(Mob mob) { return mob.saveWithoutId(new CompoundTag()); }
    /** Called after snapshot restoration, before adding the entity to the world. */
    default void prepareSummon(Mob mob, ServerPlayer owner) {}
    /** Install movement/target overrides without removing native attack goals. */
    default void configureCompanion(Mob mob) {
        dev.soulcatcherplus.companion.CompanionBrain.installStandardGoals(mob);
    }
    /** Optional support for nonstandard AI; called every five server ticks. */
    default void tick(Mob mob, ServerPlayer owner) {}
    default void onRecall(Mob mob, ServerPlayer owner) {}
}
