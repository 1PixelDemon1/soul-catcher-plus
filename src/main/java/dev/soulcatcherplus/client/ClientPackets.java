// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ClientPackets {
    public static void open(SoulNetwork.State state) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof SoulScreen screen) screen.update(state);
        else client.setScreen(new SoulScreen(state));
    }
    public static void ghost(SoulNetwork.Ghost state) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(state.entityId());
        if (entity != null) entity.getPersistentData().putUUID(CompanionBrain.OWNER, state.owner());
    }
    private ClientPackets() {}
}
