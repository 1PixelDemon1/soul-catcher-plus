// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import net.minecraftforge.eventbus.api.event.MutableEvent;

/** Posted on MinecraftForge.EVENT_BUS before state or resource consumption. */
public abstract class SoulEvent extends MutableEvent implements net.minecraftforge.eventbus.api.event.InheritableEvent, net.minecraftforge.eventbus.api.event.characteristic.Cancellable {
    public static final net.minecraftforge.eventbus.api.bus.CancellableEventBus<SoulEvent> BUS = net.minecraftforge.eventbus.api.bus.CancellableEventBus.create(SoulEvent.class);
    public final ServerPlayer player;
    public final Mob mob;
    protected SoulEvent(ServerPlayer player, Mob mob) { this.player = player; this.mob = mob; }
    public static final class Capture extends SoulEvent {
        public static final net.minecraftforge.eventbus.api.bus.CancellableEventBus<Capture> BUS = net.minecraftforge.eventbus.api.bus.CancellableEventBus.create(Capture.class);
        public Capture(ServerPlayer player, Mob mob) { super(player, mob); }
    }
    public static final class Summon extends SoulEvent {
        public static final net.minecraftforge.eventbus.api.bus.CancellableEventBus<Summon> BUS = net.minecraftforge.eventbus.api.bus.CancellableEventBus.create(Summon.class);
        public Summon(ServerPlayer player, Mob mob) { super(player, mob); }
    }
}
