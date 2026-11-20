// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.api.client.SoulPreviews;
import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.config.SoulClientConfig;
import dev.soulcatcherplus.data.SoulAppearance;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Main-client-thread cache, keyed by owner rather than the player viewing the soul. */
@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class SoulAppearances {
    private static final Map<UUID, SoulAppearance> OWNERS = new HashMap<>();
    private static final Map<Entity, SoulAppearance> PROJECTIONS = new java.util.WeakHashMap<>();
    private static final UUID PROJECTED_OWNER = new UUID(0, 0);
    private static ClientPacketListener connection;
    private static SoulAppearance lastPublished;
    private static int ticks, nextPublishAt;

    private static void ensureSession() {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current != connection) {
            connection = current;
            OWNERS.clear();
            lastPublished = null;
            ticks = nextPublishAt = 0;
        }
    }
    public static boolean receive(UUID owner, SoulAppearance appearance, net.minecraft.network.Connection source) {
        var current = Minecraft.getInstance().getConnection();
        if (current == null || current.getConnection() != source) return false;
        ensureSession();
        if (connection != null) {
            SoulAppearance received = SoulAppearance.clamped(appearance.color(), appearance.opacity());
            OWNERS.put(owner, received);
            var player = Minecraft.getInstance().player;
            if (player != null && owner.equals(player.getUUID())) lastPublished = received;
        }
        return true;
    }
    public static SoulAppearance of(Entity entity) {
        SoulAppearance projected = PROJECTIONS.get(entity);
        if (projected != null) return projected;
        ensureSession();
        Minecraft client = Minecraft.getInstance();
        if (SoulPreviews.isPreview(entity)) return SoulClientConfig.appearance();
        if (!CompanionBrain.isSoul(entity)) return SoulAppearance.DEFAULT;
        UUID owner = CompanionBrain.owner(entity);
        if (client.player != null && owner.equals(client.player.getUUID())) return SoulClientConfig.appearance();
        return OWNERS.getOrDefault(owner, SoulAppearance.DEFAULT);
    }
    /** A detached decorative projection has its own settings, independent of player preferences. */
    public static void project(Entity entity, SoulAppearance appearance) {
        SoulPreviews.mark(entity);
        if (!CompanionBrain.isSoul(entity)) entity.getPersistentData().putUUID(CompanionBrain.OWNER, PROJECTED_OWNER);
        PROJECTIONS.put(entity, appearance);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ensureSession();
        Minecraft client = Minecraft.getInstance();
        if (connection == null || client.level == null || client.player == null) return;
        ticks++;
        SoulAppearance current = SoulClientConfig.savedAppearance();
        if (ticks < nextPublishAt || current.equals(lastPublished)) return;
        SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.AppearanceRequest(current));
        // The server's owner update acknowledges the commit. Until then, retry at most once a second;
        // a lagged packet rejected by the server limiter must not lose the player's last edit.
        nextPublishAt = ticks + 20;
    }
    private SoulAppearances() {}
}
