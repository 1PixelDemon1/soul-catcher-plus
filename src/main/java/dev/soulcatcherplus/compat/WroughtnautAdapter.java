// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.compat;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.MobEntity;

/**
 * Optional bridge: uses only Mowzie's own public methods, without packaging its
 * code/assets or requiring it when the base mod loads.
 */
public final class WroughtnautAdapter implements SoulAdapter {
    @Override public CompoundNBT previewData(CompoundNBT snapshot) {
        CompoundNBT tag = SoulAdapter.super.previewData(snapshot);
        tag.putBoolean("active", true);
        tag.putBoolean("alwaysActive", true);
        return tag;
    }
    @Override public CompoundNBT capture(MobEntity mob) {
        CompoundNBT tag = SoulAdapter.super.capture(mob);
        tag.remove("restPos");
        tag.putBoolean("active", true); tag.putBoolean("alwaysActive", true);
        return tag;
    }
    @Override public void prepareSummon(MobEntity mob, ServerPlayerEntity owner) { activate(mob); mob.restrictTo(net.minecraft.util.math.BlockPos.ZERO, -1); }
    @Override public void configureCompanion(MobEntity mob) {
        activate(mob);
        SoulAdapter.super.configureCompanion(mob);
    }
    private void activate(MobEntity mob) {
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
