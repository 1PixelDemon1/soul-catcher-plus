// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

/** Forgotten souls unlock through their scrolls, not ordinary scythe capture. */
public final class SoulShamanAdapter implements SoulAdapter {
    @Override public boolean canCapture(MobEntity mob, ServerPlayerEntity player) { return false; }
}
