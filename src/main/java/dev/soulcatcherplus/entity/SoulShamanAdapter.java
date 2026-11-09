// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.api.SoulAdapter;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;

/** Forgotten souls unlock through their scrolls, not ordinary scythe capture. */
public final class SoulShamanAdapter implements SoulAdapter {
    @Override public boolean canCapture(Mob mob, ServerPlayer player) { return false; }
}
