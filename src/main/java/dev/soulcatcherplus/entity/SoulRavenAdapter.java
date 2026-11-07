// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.ServerPlayerEntity;

/** Built-in legendary soul, unlocked by a soul scroll instead of ordinary capture. */
public final class SoulRavenAdapter implements SoulAdapter {
    @Override public boolean canCapture(MobEntity mob, ServerPlayerEntity player) { return false; }
    @Override public boolean usesStandardBrain(MobEntity mob) { return false; }
    @Override public void prepareSummon(MobEntity mob, ServerPlayerEntity owner) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4.0);
        mob.setHealth(4.0f);
    }
    @Override public void configureCompanion(MobEntity mob) {
        ((SoulRavenEntity) mob).configureSoul();
    }
}
