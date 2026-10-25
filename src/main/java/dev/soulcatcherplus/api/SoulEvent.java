// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Posted on MinecraftForge.EVENT_BUS before state or resource consumption. */
public abstract class SoulEvent extends Event {
    public final ServerPlayer player;
    public final Mob mob;
    protected SoulEvent(ServerPlayer player, Mob mob) { this.player = player; this.mob = mob; }
    @Cancelable public static final class Capture extends SoulEvent {
        public Capture(ServerPlayer player, Mob mob) { super(player, mob); }
    }
    @Cancelable public static final class Summon extends SoulEvent {
        public Summon(ServerPlayer player, Mob mob) { super(player, mob); }
    }
}
