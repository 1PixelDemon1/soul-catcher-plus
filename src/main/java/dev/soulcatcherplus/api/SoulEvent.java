// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.Event;

/** Posted on NeoForge.EVENT_BUS before state or resource consumption. */
public abstract class SoulEvent extends Event {
    public final ServerPlayer player;
    public final Mob mob;
    protected SoulEvent(ServerPlayer player, Mob mob) { this.player = player; this.mob = mob; }
    public static final class Capture extends SoulEvent implements ICancellableEvent {
        public Capture(ServerPlayer player, Mob mob) { super(player, mob); }
    }
    public static final class Summon extends SoulEvent implements ICancellableEvent {
        public Summon(ServerPlayer player, Mob mob) { super(player, mob); }
    }
}
