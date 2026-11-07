// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;

/** Legendary souls enter the collection through scrolls, rather than capture. */
public final class SoulCourierAdapter implements SoulAdapter {
    @Override public boolean canCapture(MobEntity mob, ServerPlayerEntity player) { return false; }
}
