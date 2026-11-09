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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import dev.soulcatcherplus.network.ClientDispatch;
import net.minecraftforge.network.*;
import net.minecraftforge.network.SimpleChannel;

public final class SoulNetwork {
    public static void sendToServer(Object message) { CHANNEL.send(message,PacketDistributor.SERVER.noArg()); }
    private static <T> void registerMessage(int id, Class<T> type, java.util.function.BiConsumer<T,FriendlyByteBuf> encoder,
            java.util.function.Function<FriendlyByteBuf,T> decoder, java.util.function.BiConsumer<T,net.minecraftforge.event.network.CustomPayloadEvent.Context> handler,
            Optional<NetworkDirection> direction) {
        CHANNEL.messageBuilder(type,id,direction.orElseThrow()).encoder(encoder).decoder(decoder).consumerNetworkThread(handler).add();
    }
    private static final int VERSION = 12;
    // Even two 256-character UTF-8 strings per row keep a part below 64 KiB.
    public static final int STATE_ROWS_PER_PART = 32;
    private static final int MAX_PREVIEW_BYTES = 16 * 1024;
    // Server-thread-only, weakly held session state: unlike world time this clock runs while paused.
    private static final Map<ServerPlayer, UiRateLimit> UI_RATE_LIMITS = new WeakHashMap<>();
    private static final class UiRateLimit {
        long action, preview, refresh, wheelRefresh;
    }
    public static final SimpleChannel CHANNEL = ChannelBuilder.named(Identifier.fromNamespaceAndPath(SoulCatcherPlus.ID,"main")).networkProtocolVersion(VERSION).simpleChannel();
    public record Row(UUID id, String entityType, String name, int cost, double maxHealth, boolean active,
            long cooldown, String command, SummonRules.Status status, boolean favorite) {}
    public record State(boolean openScreen, int energy, int cageCapacity, int cageSlot, int activeCount, int summonLimit,
            boolean commandMode, boolean group, UUID selected, List<Row> rows) {
        static State read(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            int energy = b.readVarInt(), cageCapacity = b.readVarInt(), cageSlot = b.readInt();
            int activeCount = b.readVarInt(), limit = b.readVarInt();
            boolean mode = b.readBoolean(), group = b.readBoolean();
            UUID selected = b.readBoolean() ? b.readUUID() : null;
            int size = b.readVarInt();
            if (size < 0 || size > STATE_ROWS_PER_PART) throw new IllegalArgumentException("Invalid soul state part size");
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < size; i++) rows.add(new Row(b.readUUID(), b.readUtf(256), b.readUtf(256), b.readVarInt(),
                b.readDouble(), b.readBoolean(), b.readVarLong(), b.readUtf(16), b.readEnum(SummonRules.Status.class), b.readBoolean()));
            return new State(open, energy, cageCapacity, cageSlot, activeCount, limit, mode, group, selected, com.google.common.collect.ImmutableList.copyOf(rows));
        }
        void write(FriendlyByteBuf b) {
            if (rows.size() > STATE_ROWS_PER_PART) throw new IllegalArgumentException("Soul state part too large");
            b.writeBoolean(openScreen);
            b.writeVarInt(energy); b.writeVarInt(cageCapacity); b.writeInt(cageSlot);
            b.writeVarInt(activeCount); b.writeVarInt(summonLimit);
            b.writeBoolean(commandMode); b.writeBoolean(group);
            b.writeBoolean(selected != null); if (selected != null) b.writeUUID(selected);
            b.writeVarInt(rows.size());
            for (Row row : rows) { b.writeUUID(row.id); b.writeUtf(row.entityType, 256); b.writeUtf(row.name, 256); b.writeVarInt(row.cost);
                b.writeDouble(row.maxHealth); b.writeBoolean(row.active); b.writeVarLong(row.cooldown);
                b.writeUtf(row.command, 16); b.writeEnum(row.status); b.writeBoolean(row.favorite); }
        }
    }
    public record StatePart(UUID snapshot, int offset, int total, State state) {
        static StatePart read(FriendlyByteBuf b) {
            UUID snapshot = b.readUUID();
            int offset = b.readVarInt(), total = b.readVarInt();
            State state = State.read(b);
            if (total < 0 || total > PlayerSouls.MAX_ENTRIES || offset < 0 || offset > total
                    || offset + state.rows().size() > total || (total > 0 && state.rows().isEmpty()))
                throw new IllegalArgumentException("Invalid soul snapshot bounds");
            return new StatePart(snapshot, offset, total, state);
        }
        void write(FriendlyByteBuf b) {
            b.writeUUID(snapshot); b.writeVarInt(offset); b.writeVarInt(total); state.write(b);
        }
    }
    public enum Action { SELECT, SUMMON, RECALL, RECALL_ALL, FOLLOW, WAIT, GUARD, TOGGLE_MODE, TOGGLE_GROUP, REFRESH, OPEN, TOGGLE_FAVORITE, AGGRESSIVE, FORGET_FORM, TELEPORT }
    public record Request(Action action, UUID entry) {
        static Request read(FriendlyByteBuf b) {
            return new Request(b.readEnum(Action.class), b.readBoolean() ? b.readUUID() : null);
        }
        void write(FriendlyByteBuf b) { b.writeEnum(action); b.writeBoolean(entry != null); if (entry != null) b.writeUUID(entry); }
    }
    public enum WheelAction { OPEN, REFRESH, FOLLOW, WAIT, GUARD, RECALL, AGGRESSIVE, TELEPORT }
    public record WheelRequest(WheelAction action, UUID entry, boolean group) {
        public WheelRequest(WheelAction action, UUID entry) { this(action, entry, false); }
        static WheelRequest read(FriendlyByteBuf b) {
            return new WheelRequest(b.readEnum(WheelAction.class), b.readBoolean() ? b.readUUID() : null, b.readBoolean());
        }
        void write(FriendlyByteBuf b) {
            b.writeEnum(action); b.writeBoolean(entry != null); if (entry != null) b.writeUUID(entry);
            b.writeBoolean(group);
        }
    }
    public record Companion(UUID entry, String name, float health, float maxHealth, String command, boolean loaded) {}
    /** Active-only snapshot: health is live server data; unloaded companions have explicitly unknown HP. */
    public record WheelState(int energy, int cageCapacity, int cageSlot, List<Companion> companions) {
        static WheelState read(FriendlyByteBuf b) {
            int energy = b.readVarInt(), cageCapacity = b.readVarInt(), cageSlot = b.readInt(), size = b.readVarInt();
            if (size < 0 || size > PlayerSouls.MAX_ENTRIES) throw new IllegalArgumentException("Invalid companion count");
            List<Companion> companions = new ArrayList<>();
            for (int i = 0; i < size; i++) companions.add(new Companion(b.readUUID(), b.readUtf(256),
                b.readFloat(), b.readFloat(), b.readUtf(16), b.readBoolean()));
            return new WheelState(energy, cageCapacity, cageSlot, com.google.common.collect.ImmutableList.copyOf(companions));
        }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(energy); b.writeVarInt(cageCapacity); b.writeInt(cageSlot); b.writeVarInt(companions.size());
            for (Companion companion : companions) {
                b.writeUUID(companion.entry); b.writeUtf(companion.name, 256);
                b.writeFloat(companion.health); b.writeFloat(companion.maxHealth);
                b.writeUtf(companion.command, 16); b.writeBoolean(companion.loaded);
            }
        }
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
            net.minecraft.nbt.Tag raw = b.readNbt(NbtAccounter.create(2 * 1024 * 1024));
            if (!(raw instanceof CompoundTag appearance)) throw new IllegalArgumentException("Invalid soul appearance payload");
            return new Preview(entry, type, appearance == null ? new CompoundTag() : appearance);
        }
        void write(FriendlyByteBuf b) { b.writeUUID(entry); b.writeUtf(entityType, 256); b.writeNbt(appearance); }
    }
    /** Visual-only departure; includes a fallback envelope if the entity is already untracked. */
    public record Dissolve(int entityId, UUID entityUuid, Identifier dimension,
            double x, double y, double z, float width, float height) {
        static Dissolve read(FriendlyByteBuf b) {
            return new Dissolve(b.readVarInt(), b.readUUID(), b.readIdentifier(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
        }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(entityId); b.writeUUID(entityUuid); b.writeIdentifier(dimension);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat(width); b.writeFloat(height);
        }
    }
    public static void register() {
        registerMessage(0, StatePart.class, StatePart::write, StatePart::read, SoulNetwork::state,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(1, Request.class, Request::write, Request::read, SoulNetwork::request,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(2, Ghost.class, Ghost::write, Ghost::read, SoulNetwork::ghostPacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(3, PreviewRequest.class, PreviewRequest::write, PreviewRequest::read, SoulNetwork::previewRequest,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(4, Preview.class, Preview::write, Preview::read, SoulNetwork::previewPacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(5, Dissolve.class, Dissolve::write, Dissolve::read, SoulNetwork::dissolvePacket,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registerMessage(6, WheelRequest.class, WheelRequest::write, WheelRequest::read, SoulNetwork::wheelRequest,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        registerMessage(7, WheelState.class, WheelState::write, WheelState::read, SoulNetwork::wheelState,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.build();
    }
    public static void open(ServerPlayer player) {
        if (SoulService.holdsScythe(player)) sendState(player, true);
    }
    private static void sendState(ServerPlayer player, boolean openScreen) {
        PlayerSouls data = SoulService.data(player);
        SoulCages.Active cage = SoulCages.active(player);
        SummonRules.Context availabilityContext = SoulService.summonContext(player);
        List<Row> rows = data.entries.values().stream().map(e -> {
            Entity active = e.activeEntity == null ? null : player.level().getEntity(e.activeEntity);
            String command = active == null ? "" : active.getPersistentData().getStringOr(CompanionBrain.MODE, "");
            SummonRules.Availability availability = SummonRules.inspect(availabilityContext, e);
            return new Row(e.id, e.entityType, abbreviate(e.name), e.cost, e.maxHealth, e.activeEntity != null,
                availability.recoverySeconds(), command, availability.status(), data.isFavorite(e.entityType));
        }).collect(com.google.common.collect.ImmutableList.toImmutableList());
        UUID snapshot = UUID.randomUUID();
        // Always send one part, including an empty collection. Clients apply only a complete snapshot.
        for (int offset = 0; offset < Math.max(1, rows.size()); offset += STATE_ROWS_PER_PART) {
            State part = new State(openScreen, cage.energy(), cage.capacity(), cage.slot(), availabilityContext.activeCount(), availabilityContext.limit(),
                data.commandMode, data.commandGroup, data.selected,
                com.google.common.collect.ImmutableList.copyOf(rows.subList(offset, Math.min(rows.size(), offset + STATE_ROWS_PER_PART))));
            CHANNEL.send(new StatePart(snapshot, offset, rows.size(), part), PacketDistributor.PLAYER.with(player));
        }
    }
    public static void ghost(ServerPlayer player, Entity entity) {
        CHANNEL.send(new Ghost(entity.getId(), CompanionBrain.owner(entity)), PacketDistributor.PLAYER.with(player));
    }
    public static void ghost(Entity entity) {
        CHANNEL.send(new Ghost(entity.getId(), CompanionBrain.owner(entity)), PacketDistributor.TRACKING_ENTITY.with(entity));
    }
    public static void dissolve(Mob mob) {
        if (mob.level().isClientSide() || mob.getPersistentData().getBooleanOr("scp_dissolving", false)) return;
        mob.getPersistentData().putBoolean("scp_dissolving", true);
        // Send before the removal packet; clients retain a short-lived visual echo only.
        CHANNEL.send(new Dissolve(mob.getId(), mob.getUUID(),
            mob.level().dimension().identifier(), mob.getX(), mob.getY(), mob.getZ(), mob.getBbWidth(), mob.getBbHeight()), PacketDistributor.TRACKING_ENTITY.with(mob));
    }
    private static void request(Request packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
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
            if (packet.action != Action.TOGGLE_FAVORITE && packet.action != Action.FORGET_FORM
                    && data.entries.containsKey(packet.entry)) data.selected = packet.entry;
            switch (packet.action) {
                case SELECT -> { if (data.entries.containsKey(packet.entry)) data.selected = packet.entry; }
                case SUMMON -> { if (packet.entry != null) SoulService.summon(player, packet.entry); }
                case RECALL -> { SoulEntry entry = data.entries.get(packet.entry); if (entry != null) SoulService.recall(player.level().getServer(), entry); }
                case RECALL_ALL -> SoulService.recallAll(player);
                case FOLLOW -> SoulService.command(player, CompanionBrain.Mode.FOLLOW);
                case WAIT -> SoulService.command(player, CompanionBrain.Mode.WAIT);
                case GUARD -> SoulService.command(player, CompanionBrain.Mode.GUARD);
                case AGGRESSIVE -> SoulService.command(player, CompanionBrain.Mode.AGGRESSIVE);
                case TELEPORT -> SoulService.teleport(player);
                case TOGGLE_MODE -> data.commandMode = !data.commandMode;
                case TOGGLE_GROUP -> data.commandGroup = !data.commandGroup;
                case TOGGLE_FAVORITE -> data.toggleFavorite(packet.entry);
                case FORGET_FORM -> SoulService.forgetForm(player, packet.entry);
                case OPEN -> { open(player); return; }
                case REFRESH -> { return; }
            }
            SoulService.changed(player);
            sendState(player, false);
        });
        context.setPacketHandled(true);
    }
    private static void state(StatePart packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ClientDispatch.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.statePart(packet)));
        context.setPacketHandled(true);
    }
    private static void sendWheelState(ServerPlayer player) {
        PlayerSouls data = SoulService.data(player);
        SoulCages.Active cage = SoulCages.active(player);
        List<Companion> companions = data.entries.values().stream().filter(e -> e.activeEntity != null).map(entry -> {
            Mob mob = SoulService.activeMob(player, entry);
            boolean loaded = mob != null && Float.isFinite(mob.getHealth()) && Float.isFinite(mob.getMaxHealth());
            return new Companion(entry.id, abbreviate(entry.name), loaded ? Math.max(0, mob.getHealth()) : 0,
                loaded ? Math.max(1, mob.getMaxHealth()) : 0, loaded ? CompanionBrain.mode(mob).name() : "", loaded);
        }).collect(com.google.common.collect.ImmutableList.toImmutableList());
        CHANNEL.send(new WheelState(cage.energy(), cage.capacity(), cage.slot(), companions), PacketDistributor.PLAYER.with(player));
    }
    private static void wheelRequest(WheelRequest packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
            if (!SoulService.holdsScythe(player)) return;
            long now = System.nanoTime();
            UiRateLimit rate = UI_RATE_LIMITS.computeIfAbsent(player, p -> new UiRateLimit());
            if (packet.action == WheelAction.OPEN || packet.action == WheelAction.REFRESH) {
                if (rate.wheelRefresh != 0 && now - rate.wheelRefresh < 0) return;
                rate.wheelRefresh = now + 250_000_000L;
            } else {
                if (rate.action != 0 && now - rate.action < 0) return;
                rate.action = now + 100_000_000L;
                SoulEntry entry = packet.group ? null : SoulService.data(player).entries.get(packet.entry);
                if (!packet.group && (entry == null || entry.activeEntity == null)) return;
                if (packet.action == WheelAction.RECALL) {
                    if (packet.group) SoulService.recallAll(player);
                    else SoulService.recall(player.level().getServer(), entry);
                } else if (packet.action == WheelAction.TELEPORT) {
                    if (packet.group) SoulService.teleportAll(player);
                    else SoulService.teleportOne(player, entry.id);
                } else {
                    CompanionBrain.Mode mode = switch (packet.action) {
                        case FOLLOW -> CompanionBrain.Mode.FOLLOW;
                        case WAIT -> CompanionBrain.Mode.WAIT;
                        case GUARD -> CompanionBrain.Mode.GUARD;
                        case AGGRESSIVE -> CompanionBrain.Mode.AGGRESSIVE;
                        default -> null;
                    };
                    if (mode == null) return;
                    if (packet.group) SoulService.commandAll(player, mode);
                    else SoulService.commandOne(player, entry.id, mode);
                }
            }
            sendWheelState(player);
        });
        context.setPacketHandled(true);
    }
    private static void wheelState(WheelState packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ClientDispatch.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.wheel(packet)));
        context.setPacketHandled(true);
    }
    private static void ghostPacket(Ghost packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ClientDispatch.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.ghost(packet)));
        context.setPacketHandled(true);
    }
    private static void previewRequest(PreviewRequest packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
            long now = System.nanoTime();
            UiRateLimit rate = UI_RATE_LIMITS.computeIfAbsent(player, p -> new UiRateLimit());
            if (rate.preview != 0 && now - rate.preview < 0) return;
            rate.preview = now + 100_000_000L;
            SoulEntry entry = SoulService.data(player).entries.get(packet.entry);
            if (entry == null) return;
            CompoundTag appearance = new CompoundTag();
            try {
                CompoundTag visual = SoulAdapters.get(Identifier.parse(entry.entityType)).previewData(entry.snapshot());
                // Never transmit identity, ownership, health, passengers, or the full server snapshot.
                visual = SoulSnapshots.sanitize(visual);
                visual.remove("ForgeData"); visual.remove("ForgeCaps");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.write(visual, new DataOutputStream(bytes));
                if (bytes.size() <= MAX_PREVIEW_BYTES) appearance = visual;
                else org.apache.logging.log4j.LogManager.getLogger().warn("Soul preview data too large for {}", entry.entityType);
            } catch (RuntimeException | IOException error) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Unable to prepare soul preview for {}", entry.entityType, error);
            }
            CHANNEL.send(new Preview(entry.id, entry.entityType, appearance), PacketDistributor.PLAYER.with(player));
        });
        context.setPacketHandled(true);
    }
    private static void previewPacket(Preview packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ClientDispatch.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.preview(packet)));
        context.setPacketHandled(true);
    }
    private static void dissolvePacket(Dissolve packet, net.minecraftforge.event.network.CustomPayloadEvent.Context context) {
        context.enqueueWork(() -> ClientDispatch.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.dissolve(packet)));
        context.setPacketHandled(true);
    }
    private static String abbreviate(String name) { return name.length() <= 256 ? name : name.substring(0, 256); }
    private SoulNetwork() {}
}
