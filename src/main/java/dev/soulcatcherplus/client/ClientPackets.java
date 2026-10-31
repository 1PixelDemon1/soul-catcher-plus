// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientPackets {
    private static PendingState pending;
    private static final class PendingState {
        final UUID id;
        final int total;
        final SoulNetwork.State header;
        final List<SoulNetwork.Row> rows = new ArrayList<>();
        final long expires = System.nanoTime() + 10_000_000_000L;
        PendingState(SoulNetwork.StatePart part) { id = part.snapshot(); total = part.total(); header = part.state(); }
    }
    /** Main-client-thread-only assembly; partial snapshots never replace the displayed collection. */
    public static void statePart(SoulNetwork.StatePart part) {
        if (part.offset() == 0) pending = new PendingState(part);
        PendingState assembly = pending;
        if (assembly == null || !assembly.id.equals(part.snapshot()) || assembly.total != part.total()
                || assembly.rows.size() != part.offset() || System.nanoTime() - assembly.expires >= 0) {
            pending = null;
            return;
        }
        assembly.rows.addAll(part.state().rows());
        if (assembly.rows.size() != assembly.total) return;
        pending = null;
        SoulNetwork.State header = assembly.header;
        open(new SoulNetwork.State(header.openScreen(), header.energy(), header.cageCapacity(), header.cageSlot(),
            header.activeCount(), header.summonLimit(), header.commandMode(), header.group(), header.selected(),
            com.google.common.collect.ImmutableList.copyOf(assembly.rows)));
    }
    public static void open(SoulNetwork.State state) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SoulScreen screen) screen.update(state);
        else if (state.openScreen() && client.screen == null && ClientControls.canUseMenus(client))
            client.setScreen(new SoulScreen(state));
    }
    public static void wheel(SoulNetwork.WheelState state) {
        // A late response must never reopen a wheel after its trigger was released.
        if (Minecraft.getInstance().screen instanceof SoulWheelScreen screen) screen.update(state);
    }
    public static void ghost(SoulNetwork.Ghost state) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(state.entityId());
        if (entity != null) entity.getPersistentData().putUUID(CompanionBrain.OWNER, state.owner());
    }
    public static void preview(SoulNetwork.Preview preview) {
        if (Minecraft.getInstance().screen instanceof SoulScreen screen) screen.receivePreview(preview);
    }
    public static void dissolve(SoulNetwork.Dissolve dissolve) { SoulDissolves.start(dissolve); }
    private ClientPackets() {}
}
