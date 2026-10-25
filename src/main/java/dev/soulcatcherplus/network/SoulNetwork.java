// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.network;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.*;
import dev.soulcatcherplus.data.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SoulNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(SoulCatcherPlus.ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    public record Row(UUID id, String name, int cost, int slots, boolean active, long cooldown) {}
    public record State(int energy, int slots, boolean commandMode, boolean group, UUID selected, List<Row> rows) {
        static State read(FriendlyByteBuf b) {
            int energy = b.readVarInt(), slots = b.readVarInt(); boolean mode = b.readBoolean(), group = b.readBoolean();
            UUID selected = b.readBoolean() ? b.readUUID() : null;
            int size = b.readVarInt();
            if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid soul collection size");
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < size; i++) rows.add(new Row(b.readUUID(), b.readUtf(256), b.readVarInt(),
                b.readVarInt(), b.readBoolean(), b.readVarLong()));
            return new State(energy, slots, mode, group, selected, List.copyOf(rows));
        }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(energy); b.writeVarInt(slots); b.writeBoolean(commandMode); b.writeBoolean(group);
            b.writeBoolean(selected != null); if (selected != null) b.writeUUID(selected);
            b.writeVarInt(rows.size());
            for (Row row : rows) { b.writeUUID(row.id); b.writeUtf(row.name, 256); b.writeVarInt(row.cost);
                b.writeVarInt(row.slots); b.writeBoolean(row.active); b.writeVarLong(row.cooldown); }
        }
    }
    public enum Action { SELECT, SUMMON, RECALL, RECALL_ALL, FOLLOW, WAIT, GUARD, TOGGLE_MODE, TOGGLE_GROUP }
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
    public static void register() {
        CHANNEL.registerMessage(0, State.class, State::write, State::read, SoulNetwork::state,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Request.class, Request::write, Request::read, SoulNetwork::request,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Ghost.class, Ghost::write, Ghost::read, SoulNetwork::ghostPacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void open(ServerPlayer player) {
        PlayerSouls data = SoulService.data(player);
        long now = player.server.overworld().getGameTime();
        List<Row> rows = data.entries.values().stream().map(e -> new Row(e.id, e.name, e.cost, e.slots,
            e.activeEntity != null, Math.max(0, (e.readyAt - now + 19) / 20))).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new State(data.energy, data.usedSlots(), data.commandMode, data.commandGroup, data.selected, rows));
    }
    public static void ghost(ServerPlayer player, Entity entity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Ghost(entity.getId(), CompanionBrain.owner(entity)));
    }
    private static void request(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator() || !SoulService.holdsScythe(player)) return;
            // Per-player throttle; packet state is never accepted as authority.
            long now = player.server.overworld().getGameTime();
            if (player.getPersistentData().getLong("scp_next_action") > now) return;
            player.getPersistentData().putLong("scp_next_action", now + 2);
            PlayerSouls data = SoulService.data(player);
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
            }
            SoulService.changed(player);
            open(player);
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
    private SoulNetwork() {}
}
