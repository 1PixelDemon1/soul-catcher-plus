// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;

/** Legendary souls enter the collection through scrolls, rather than capture. */
public final class SoulCourierAdapter implements SoulAdapter {
    @Override public boolean canCapture(Mob mob, ServerPlayer player) { return false; }
}
