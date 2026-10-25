// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.compat;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Optional bridge: uses only Mowzie's own public methods, without packaging its
 * code/assets or requiring it when the base mod loads.
 */
public final class WroughtnautAdapter implements SoulAdapter {
    @Override public CompoundTag capture(Mob mob) {
        CompoundTag tag = SoulAdapter.super.capture(mob);
        tag.remove("restPos");
        tag.putBoolean("active", true); tag.putBoolean("alwaysActive", true);
        return tag;
    }
    @Override public void prepareSummon(Mob mob, ServerPlayer owner) { activate(mob); mob.clearRestriction(); }
    @Override public void configureCompanion(Mob mob) {
        activate(mob);
        SoulAdapter.super.configureCompanion(mob);
    }
    private void activate(Mob mob) {
        try {
            Class<?> type = mob.getClass();
            boolean always = (Boolean) type.getMethod("isAlwaysActive").invoke(mob);
            if (!always) type.getMethod("setAlwaysActive", boolean.class).invoke(mob, true);
            type.getMethod("setActive", boolean.class).invoke(mob, true);
            type.getField("active").setBoolean(mob, true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported Mowzie's Mobs Wroughtnaut API", error);
        }
    }
}
