// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;

/** Built-in legendary soul, unlocked by a soul scroll instead of ordinary capture. */
public final class SoulRavenAdapter implements SoulAdapter {
    @Override public boolean canCapture(Mob mob, ServerPlayer player) { return false; }
    @Override public boolean usesStandardBrain(Mob mob) { return false; }
    @Override public void prepareSummon(Mob mob, ServerPlayer owner) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4.0);
        mob.setHealth(4.0f);
    }
    @Override public void configureCompanion(Mob mob) {
        ((SoulRavenEntity) mob).configureSoul();
    }
}
