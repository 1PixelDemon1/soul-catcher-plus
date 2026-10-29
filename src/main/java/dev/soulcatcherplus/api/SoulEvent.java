// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Posted on MinecraftForge.EVENT_BUS before state or resource consumption. */
public abstract class SoulEvent extends Event {
    public final ServerPlayerEntity player;
    public final MobEntity mob;
    protected SoulEvent(ServerPlayerEntity player, MobEntity mob) { this.player = player; this.mob = mob; }
    @Cancelable public static final class Capture extends SoulEvent {
        public Capture(ServerPlayerEntity player, MobEntity mob) { super(player, mob); }
    }
    @Cancelable public static final class Summon extends SoulEvent {
        public Summon(ServerPlayerEntity player, MobEntity mob) { super(player, mob); }
    }
}
