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
import net.minecraft.network.PacketBuffer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class SoulNetwork {
    private static final String VERSION = "9";
    private static final int MAX_PREVIEW_BYTES = 16 * 1024;
    // Server-thread-only, weakly held session state: unlike world time this clock runs while paused.
    private static final Map<ServerPlayerEntity, UiRateLimit> UI_RATE_LIMITS = new WeakHashMap<>();
    private static final class UiRateLimit {
        long action, preview, refresh, wheelRefresh;
    }
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(SoulCatcherPlus.ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    @com.github.bsideup.jabel.Desugar
    public record Row(UUID id, String entityType, String name, int cost, double maxHealth, boolean active,
            long cooldown, String command, SummonRules.Status status, boolean favorite) {}
    @com.github.bsideup.jabel.Desugar
    public record State(boolean openScreen, int energy, int cageCapacity, int cageSlot, int activeCount, int summonLimit,
            boolean commandMode, boolean group, UUID selected, List<Row> rows) {
        static State read(PacketBuffer b) {
            boolean open = b.readBoolean();
            int energy = b.readVarInt(), cageCapacity = b.readVarInt(), cageSlot = b.readInt();
            int activeCount = b.readVarInt(), limit = b.readVarInt();
            boolean mode = b.readBoolean(), group = b.readBoolean();
            UUID selected = b.readBoolean() ? b.readUUID() : null;
            int size = b.readVarInt();
            if (size < 0 || size > PlayerSouls.MAX_ENTRIES) throw new IllegalArgumentException("Invalid soul collection size");
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < size; i++) rows.add(new Row(b.readUUID(), b.readUtf(256), b.readUtf(256), b.readVarInt(),
                b.readDouble(), b.readBoolean(), b.readVarLong(), b.readUtf(16), b.readEnum(SummonRules.Status.class), b.readBoolean()));
            return new State(open, energy, cageCapacity, cageSlot, activeCount, limit, mode, group, selected, com.google.common.collect.ImmutableList.copyOf(rows));
        }
        void write(PacketBuffer b) {
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
    public enum Action { SELECT, SUMMON, RECALL, RECALL_ALL, FOLLOW, WAIT, GUARD, TOGGLE_MODE, TOGGLE_GROUP, REFRESH, OPEN, TOGGLE_FAVORITE, AGGRESSIVE }
    @com.github.bsideup.jabel.Desugar
    public record Request(Action action, UUID entry) {
        static Request read(PacketBuffer b) {
            return new Request(b.readEnum(Action.class), b.readBoolean() ? b.readUUID() : null);
        }
        void write(PacketBuffer b) { b.writeEnum(action); b.writeBoolean(entry != null); if (entry != null) b.writeUUID(entry); }
    }
    public enum WheelAction { OPEN, REFRESH, FOLLOW, WAIT, GUARD, RECALL, AGGRESSIVE }
    @com.github.bsideup.jabel.Desugar
    public record WheelRequest(WheelAction action, UUID entry, boolean group) {
        public WheelRequest(WheelAction action, UUID entry) { this(action, entry, false); }
        static WheelRequest read(PacketBuffer b) {
            return new WheelRequest(b.readEnum(WheelAction.class), b.readBoolean() ? b.readUUID() : null, b.readBoolean());
        }
        void write(PacketBuffer b) {
            b.writeEnum(action); b.writeBoolean(entry != null); if (entry != null) b.writeUUID(entry);
            b.writeBoolean(group);
        }
    }
    @com.github.bsideup.jabel.Desugar
    public record Companion(UUID entry, String name, float health, float maxHealth, String command, boolean loaded) {}
    /** Active-only snapshot: health is live server data; unloaded companions have explicitly unknown HP. */
    @com.github.bsideup.jabel.Desugar
    public record WheelState(int energy, int cageCapacity, int cageSlot, List<Companion> companions) {
        static WheelState read(PacketBuffer b) {
            int energy = b.readVarInt(), cageCapacity = b.readVarInt(), cageSlot = b.readInt(), size = b.readVarInt();
            if (size < 0 || size > PlayerSouls.MAX_ENTRIES) throw new IllegalArgumentException("Invalid companion count");
            List<Companion> companions = new ArrayList<>();
            for (int i = 0; i < size; i++) companions.add(new Companion(b.readUUID(), b.readUtf(256),
                b.readFloat(), b.readFloat(), b.readUtf(16), b.readBoolean()));
            return new WheelState(energy, cageCapacity, cageSlot, com.google.common.collect.ImmutableList.copyOf(companions));
        }
        void write(PacketBuffer b) {
            b.writeVarInt(energy); b.writeVarInt(cageCapacity); b.writeInt(cageSlot); b.writeVarInt(companions.size());
            for (Companion companion : companions) {
                b.writeUUID(companion.entry); b.writeUtf(companion.name, 256);
                b.writeFloat(companion.health); b.writeFloat(companion.maxHealth);
                b.writeUtf(companion.command, 16); b.writeBoolean(companion.loaded);
            }
        }
    }
    @com.github.bsideup.jabel.Desugar
    public record Ghost(int entityId, UUID owner) {
        static Ghost read(PacketBuffer b) { return new Ghost(b.readVarInt(), b.readUUID()); }
        void write(PacketBuffer b) { b.writeVarInt(entityId); b.writeUUID(owner); }
    }
    @com.github.bsideup.jabel.Desugar
    public record PreviewRequest(UUID entry) {
        static PreviewRequest read(PacketBuffer b) { return new PreviewRequest(b.readUUID()); }
        void write(PacketBuffer b) { b.writeUUID(entry); }
    }
    @com.github.bsideup.jabel.Desugar
    public record Preview(UUID entry, String entityType, CompoundNBT appearance) {
        static Preview read(PacketBuffer b) {
            UUID entry = b.readUUID();
            String type = b.readUtf(256);
            CompoundNBT appearance = b.readNbt(new NBTSizeTracker(2 * 1024 * 1024));
            return new Preview(entry, type, appearance == null ? new CompoundNBT() : appearance);
        }
        void write(PacketBuffer b) { b.writeUUID(entry); b.writeUtf(entityType, 256); b.writeNbt(appearance); }
    }
    /** Visual-only departure; includes a fallback envelope if the entity is already untracked. */
    @com.github.bsideup.jabel.Desugar
    public record Dissolve(int entityId, UUID entityUuid, ResourceLocation dimension,
            double x, double y, double z, float width, float height) {
        static Dissolve read(PacketBuffer b) {
            return new Dissolve(b.readVarInt(), b.readUUID(), b.readResourceLocation(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat());
        }
        void write(PacketBuffer b) {
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
        CHANNEL.registerMessage(6, WheelRequest.class, WheelRequest::write, WheelRequest::read, SoulNetwork::wheelRequest,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, WheelState.class, WheelState::write, WheelState::read, SoulNetwork::wheelState,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void open(ServerPlayerEntity player) {
        if (SoulService.holdsScythe(player)) sendState(player, true);
    }
    private static void sendState(ServerPlayerEntity player, boolean openScreen) {
        PlayerSouls data = SoulService.data(player);
        SoulCages.Active cage = SoulCages.active(player);
        SummonRules.Context availabilityContext = SoulService.summonContext(player);
        List<Row> rows = data.entries.values().stream().map(e -> {
            Entity active = e.activeEntity == null ? null : player.getLevel().getEntity(e.activeEntity);
            String command = active == null ? "" : active.getPersistentData().getString(CompanionBrain.MODE);
            SummonRules.Availability availability = SummonRules.inspect(availabilityContext, e);
            return new Row(e.id, e.entityType, abbreviate(e.name), e.cost, e.maxHealth, e.activeEntity != null,
                availability.recoverySeconds(), command, availability.status(), data.isFavorite(e.entityType));
        }).collect(com.google.common.collect.ImmutableList.toImmutableList());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new State(openScreen, cage.energy(), cage.capacity(), cage.slot(), availabilityContext.activeCount(), availabilityContext.limit(),
                data.commandMode, data.commandGroup, data.selected, rows));
    }
    public static void ghost(ServerPlayerEntity player, Entity entity) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Ghost(entity.getId(), CompanionBrain.owner(entity)));
    }
    public static void ghost(Entity entity) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), new Ghost(entity.getId(), CompanionBrain.owner(entity)));
    }
    public static void dissolve(MobEntity mob) {
        if (mob.level.isClientSide || mob.getPersistentData().getBoolean("scp_dissolving")) return;
        mob.getPersistentData().putBoolean("scp_dissolving", true);
        // Send before the removal packet; clients retain a short-lived visual echo only.
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new Dissolve(mob.getId(), mob.getUUID(),
            mob.level.dimension().location(), mob.getX(), mob.getY(), mob.getZ(), mob.getBbWidth(), mob.getBbHeight()));
    }
    private static void request(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
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
            if (packet.action != Action.TOGGLE_FAVORITE && data.entries.containsKey(packet.entry)) data.selected = packet.entry;
            switch (packet.action) {
                case SELECT -> { if (data.entries.containsKey(packet.entry)) data.selected = packet.entry; }
                case SUMMON -> { if (packet.entry != null) SoulService.summon(player, packet.entry); }
                case RECALL -> { SoulEntry entry = data.entries.get(packet.entry); if (entry != null) SoulService.recall(player.server, entry); }
                case RECALL_ALL -> SoulService.recallAll(player);
                case FOLLOW -> SoulService.command(player, CompanionBrain.Mode.FOLLOW);
                case WAIT -> SoulService.command(player, CompanionBrain.Mode.WAIT);
                case GUARD -> SoulService.command(player, CompanionBrain.Mode.GUARD);
                case AGGRESSIVE -> SoulService.command(player, CompanionBrain.Mode.AGGRESSIVE);
                case TOGGLE_MODE -> data.commandMode = !data.commandMode;
                case TOGGLE_GROUP -> data.commandGroup = !data.commandGroup;
                case TOGGLE_FAVORITE -> data.toggleFavorite(packet.entry);
                case OPEN -> { open(player); return; }
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
    private static void sendWheelState(ServerPlayerEntity player) {
        PlayerSouls data = SoulService.data(player);
        SoulCages.Active cage = SoulCages.active(player);
        List<Companion> companions = data.entries.values().stream().filter(e -> e.activeEntity != null).map(entry -> {
            MobEntity mob = SoulService.activeMob(player, entry);
            boolean loaded = mob != null && Float.isFinite(mob.getHealth()) && Float.isFinite(mob.getMaxHealth());
            return new Companion(entry.id, abbreviate(entry.name), loaded ? Math.max(0, mob.getHealth()) : 0,
                loaded ? Math.max(1, mob.getMaxHealth()) : 0, loaded ? CompanionBrain.mode(mob).name() : "", loaded);
        }).collect(com.google.common.collect.ImmutableList.toImmutableList());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WheelState(cage.energy(), cage.capacity(), cage.slot(), companions));
    }
    private static void wheelRequest(WheelRequest packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayerEntity player = context.getSender();
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
                    else SoulService.recall(player.server, entry);
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
    private static void wheelState(WheelState packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> dev.soulcatcherplus.client.ClientPackets.wheel(packet)));
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
            ServerPlayerEntity player = context.getSender();
            if (player == null || !player.isAlive() || player.isSpectator()) return;
            long now = System.nanoTime();
            UiRateLimit rate = UI_RATE_LIMITS.computeIfAbsent(player, p -> new UiRateLimit());
            if (rate.preview != 0 && now - rate.preview < 0) return;
            rate.preview = now + 100_000_000L;
            SoulEntry entry = SoulService.data(player).entries.get(packet.entry);
            if (entry == null) return;
            CompoundNBT appearance = new CompoundNBT();
            try {
                CompoundNBT visual = SoulAdapters.get(new ResourceLocation(entry.entityType)).previewData(entry.snapshot());
                // Never transmit identity, ownership, health, passengers, or the full server snapshot.
                visual = SoulSnapshots.sanitize(visual);
                visual.remove("ForgeData"); visual.remove("ForgeCaps");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                CompressedStreamTools.write(visual, new DataOutputStream(bytes));
                if (bytes.size() <= MAX_PREVIEW_BYTES) appearance = visual;
                else org.apache.logging.log4j.LogManager.getLogger().warn("Soul preview data too large for {}", entry.entityType);
            } catch (RuntimeException | IOException error) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Unable to prepare soul preview for {}", entry.entityType, error);
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
