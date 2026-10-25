// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.network;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import dev.soulcatcherplus.api.SoulAdapters;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SoulNetwork {
    private static final String VERSION = "3";
    private static final int MAX_PREVIEW_BYTES = 16 * 1024;
    // Server-thread-only, weakly held session state: unlike world time this clock runs while paused.
    private static final Map<ServerPlayer, UiRateLimit> UI_RATE_LIMITS = new WeakHashMap<>();
    private static final class UiRateLimit {
        long action, preview, refresh;
    }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(SoulCatcherPlus.ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    public record Row(UUID id, String entityType, String name, int cost, int slots, boolean active, long cooldown, String command) {}
    public record State(boolean openScreen, int energy, int slots, boolean commandMode, boolean group, UUID selected, List<Row> rows) {
        static State read(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            int energy = b.readVarInt(), slots = b.readVarInt(); boolean mode = b.readBoolean(), group = b.readBoolean();
            UUID selected = b.readBoolean() ? b.readUUID() : null;
            int size = b.readVarInt();
            if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid soul collection size");
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < size; i++) rows.add(new Row(b.readUUID(), b.readUtf(256), b.readUtf(256), b.readVarInt(),
                b.readVarInt(), b.readBoolean(), b.readVarLong(), b.readUtf(16)));
            return new State(open, energy, slots, mode, group, selected, List.copyOf(rows));
        }
        void write(FriendlyByteBuf b) {
            b.writeBoolean(openScreen);
            b.writeVarInt(energy); b.writeVarInt(slots); b.writeBoolean(commandMode); b.writeBoolean(group);
            b.writeBoolean(selected != null); if (selected != null) b.writeUUID(selected);
            b.writeVarInt(rows.size());
            for (Row row : rows) { b.writeUUID(row.id); b.writeUtf(row.entityType, 256); b.writeUtf(row.name, 256); b.writeVarInt(row.cost);
                b.writeVarInt(row.slots); b.writeBoolean(row.active); b.writeVarLong(row.cooldown); b.writeUtf(row.command, 16); }
        }
    }
    public enum Action { SELECT, SUMMON, RECALL, RECALL_ALL, FOLLOW, WAIT, GUARD, TOGGLE_MODE, TOGGLE_GROUP, REFRESH }
    public record Request(Action action, UUID entry) {
        static Request read(FriendlyByteBuf b) {
            return new Request(b.readEnum(Action.class), b.readBoolean() ? b.readUUID() : null);
        }
        void write(FriendlyByteBuf b) { b.writeEnum(action); b.writeBoolean(entry != null); if (entry != null) b.writeUUID(entry); }
    }
    public record Ghost(int entityId, UUID owner) {
        static Ghost read(FriendlyByteBuf b) { return new Ghost(b.readVarInt(), b.readUUID()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeUUID(owner); }
    }
    public record PreviewRequest(UUID entry) {
        static PreviewRequest read(FriendlyByteBuf b) { return new PreviewRequest(b.readUUID()); }
        void write(FriendlyByteBuf b) { b.writeUUID(entry); }
    }
    public record Preview(UUID entry, String entityType, CompoundTag appearance) {
        static Preview read(FriendlyByteBuf b) {
            UUID entry = b.readUUID();
            String type = b.readUtf(256);
            CompoundTag appearance = b.readNbt(new NbtAccounter(2 * 1024 * 1024));
            return new Preview(entry, type, appearance == null ? new CompoundTag() : appearance);
        }
        void write(FriendlyByteBuf b) { b.writeUUID(entry); b.writeUtf(entityType, 256); b.writeNbt(appearance); }
    }
    /** Visual-only departure; includes a fallback envelope if the entity is already untracked. */
    public record Dissolve(int entityId, UUID entityUuid, ResourceLocation dimension,
            double x, double y, double z, float width, float height) {
        static Dissolve read(FriendlyByteBuf b) {
            return new Dissolve(b.readVarInt(), b.readUUID(), b.readResourceLocation(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
        }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(entityId); b.writeUUID(entityUuid); b.writeResourceLocation(dimension);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(width); b.writeFloat(height);
        }
    }
    public static void register() {
        CHANNEL.registerMessage(0, State.class, State::write, State::read, SoulNetwork::state,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Request.class, Request::write, Request::read, SoulNetwork::request,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Ghost.class, Ghost::write, Ghost::read, SoulNetwork::ghostPacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, PreviewRequest.class, PreviewRequest::write, PreviewRequest::read, SoulNetwork::previewRequest,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, Preview.class, Preview::write, Preview::read, SoulNetwork::previewPacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, Dissolve.class, Dissolve::write, Dissolve::read, SoulNetwork::dissolvePacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void open(ServerPlayer player) {
        sendState(player, true);
    }
    private static void sendState(ServerPlayer player, boolean openScreen) {
        PlayerSouls data = SoulService.data(player);
        long now = player.server.overworld().getGameTime();
        Map<String, Long> recovery = new HashMap<>();
        data.entries.values().forEach(e -> recovery.merge(e.entityType, e.readyAt, Math::max));
        List<Row> rows = data.entries.values().stream().map(e -> {
            Entity active = e.activeEntity == null ? null : player.serverLevel().getEntity(e.activeEntity);
            String command = active == null ? "" : active.getPersistentData().getString(CompanionBrain.MODE);
            return new Row(e.id, e.entityType, abbreviate(e.name), e.cost, e.slots, e.activeEntity != null,
                Math.max(0, (recovery.getOrDefault(e.entityType, 0L) - now + 19) / 20), command);
        }).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new State(openScreen, data.energy, data.usedSlots(), data.commandMode, data.commandGroup, data.selected, rows));
    }
    public static void ghost(ServerPlayer player, Entity entity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Ghost(entity.getId(), CompanionBrain.owner(entity)));
    }
    public static void dissolve(Mob mob) {
        if (mob.level().isClientSide || mob.getPersistentData().getBoolean("scp_dissolving")) return;
        mob.getPersistentData().putBoolean("scp_dissolving", true);
        // Send before the removal packet; clients retain a short-lived visual echo only.
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new Dissolve(mob.getId(), mob.getUUID(),
            mob.level().dimension().location(), mob.getX(), mob.getY(), mob.getZ(), mob.getBbWidth(), mob.getBbHeight()));
    }
    private static void request(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator() || !SoulService.holdsScythe(player)) return;
            // Per-player throttle; packet state is never accepted as authority.
            long now = System.nanoTime();
            UiRateLimit rate = UI_RATE_LIMITS.computeIfAbsent(player, p -> new UiRateLimit());
            if (packet.action == Action.REFRESH) {
                if (rate.refresh == 0 || now - rate.refresh >= 0) {
                    rate.refresh = now + 1_000_000_000L;
                    sendState(player, false);
                }
                return;
            }
            if (rate.action != 0 && now - rate.action < 0) return;
            rate.action = now + 100_000_000L;
            PlayerSouls data = SoulService.data(player);
            if (data.entries.containsKey(packet.entry)) data.selected = packet.entry;
            switch (packet.action) {
                case SELECT -> { if (data.entries.containsKey(packet.entry)) data.selected = packet.entry; }
                case SUMMON -> { if (packet.entry != null) SoulService.summon(player, packet.entry); }
                case RECALL -> { SoulEntry entry = data.entries.get(packet.entry); if (entry != null) SoulService.recall(player.server, entry); }
                case RECALL_ALL -> SoulService.recallAll(player);
                case FOLLOW -> SoulService.command(player, CompanionBrain.Mode.FOLLOW);
                case WAIT -> SoulService.command(player, CompanionBrain.Mode.WAIT);
                case GUARD -> SoulService.command(player, CompanionBrain.Mode.GUARD);
                case TOGGLE_MODE -> data.commandMode = !data.commandMode;
                case TOGGLE_GROUP -> data.commandGroup = !data.commandGroup;
                case REFRESH -> { return; }
            }
            SoulService.changed(player);
            sendState(player, false);
        });
        context.setPacketHandled(true);
    }
    private static void state(State packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.open(packet)));
        context.setPacketHandled(true);
    }
    private static void ghostPacket(Ghost packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.ghost(packet)));
        context.setPacketHandled(true);
    }
    private static void previewRequest(PreviewRequest packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator() || !SoulService.holdsScythe(player)) return;
            long now = System.nanoTime();
            UiRateLimit rate = UI_RATE_LIMITS.computeIfAbsent(player, p -> new UiRateLimit());
            if (rate.preview != 0 && now - rate.preview < 0) return;
            rate.preview = now + 100_000_000L;
            SoulEntry entry = SoulService.data(player).entries.get(packet.entry);
            if (entry == null) return;
            CompoundTag appearance = new CompoundTag();
            try {
                CompoundTag visual = SoulAdapters.get(new ResourceLocation(entry.entityType)).previewData(entry.snapshot());
                // Never transmit identity, ownership, health, passengers, or the full server snapshot.
                visual = SoulSnapshots.sanitize(visual);
                visual.remove("ForgeData"); visual.remove("ForgeCaps");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.write(visual, new DataOutputStream(bytes));
                if (bytes.size() <= MAX_PREVIEW_BYTES) appearance = visual;
                else com.mojang.logging.LogUtils.getLogger().warn("Soul preview data too large for {}", entry.entityType);
            } catch (RuntimeException | IOException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Unable to prepare soul preview for {}", entry.entityType, error);
            }
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Preview(entry.id, entry.entityType, appearance));
        });
        context.setPacketHandled(true);
    }
    private static void previewPacket(Preview packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.preview(packet)));
        context.setPacketHandled(true);
    }
    private static void dissolvePacket(Dissolve packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.dissolve(packet)));
        context.setPacketHandled(true);
    }
    private static String abbreviate(String name) { return name.length() <= 256 ? name : name.substring(0, 256); }
    private SoulNetwork() {}
}
