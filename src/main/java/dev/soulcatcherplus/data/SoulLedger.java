// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Canonical ownership and summon state, stored in the overworld across dimensions. */
public final class SoulLedger extends SavedData {
    private final Map<UUID, PlayerSouls> players = new HashMap<>();
    public static final net.minecraft.world.level.saveddata.SavedDataType<SoulLedger> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
        net.minecraft.resources.Identifier.fromNamespaceAndPath("soulcatcherplus", "collection"), SoulLedger::new,
        CompoundTag.CODEC.xmap(SoulLedger::load, ledger -> ledger.save(new CompoundTag())), null);
    public static SoulLedger get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        SoulLedger existing = storage.get(TYPE);
        if (existing != null) return existing;
        var folder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
        // Never resurrect the legacy backup if the new file exists but cannot be read.
        if (java.nio.file.Files.exists(folder.resolve("soulcatcherplus/collection.dat")))
            throw new IllegalStateException("Unable to read Soul Catcher Plus collection; preserve the world backup");
        var legacy = folder.resolve("soulcatcherplus.dat");
        SoulLedger ledger = new SoulLedger();
        if (java.nio.file.Files.exists(legacy)) {
            try {
                var root = storage.readTagFromDisk(legacy, null, 0);
                var data = root.getCompoundOrEmpty("data").copy();
                upgradeSnapshots(data, root.getIntOr("DataVersion", 2586));
                ledger = load(data);
            } catch (java.io.IOException | RuntimeException error) {
                throw new IllegalStateException("Unable to migrate the legacy soul collection; the original file was preserved", error);
            }
        }
        storage.set(TYPE, ledger);
        return ledger;
    }
    /** Vanilla's data fixer also needs to see entities nested inside this mod's saved data. */
    private static void upgradeSnapshots(CompoundTag data, int fromVersion) {
        var players = data.getCompoundOrEmpty("Players");
        for (String player : players.keySet()) for (var raw : players.getCompoundOrEmpty(player).getListOrEmpty("Souls")) {
            if (!(raw instanceof CompoundTag entry)) continue;
            var snapshot = entry.getCompoundOrEmpty("Snapshot").copy();
            if (!entry.contains("MaxHealth")) entry.putDouble("MaxHealth", SoulBalance.legacyHealth(snapshot, entry.getIntOr("Cost", 0)));
            snapshot.putString("id", entry.getStringOr("Type", "minecraft:pig"));
            snapshot = (CompoundTag)net.minecraft.util.datafix.DataFixers.getDataFixer().update(
                net.minecraft.util.datafix.fixes.References.ENTITY,
                new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, snapshot),
                fromVersion, net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
            snapshot.remove("id");
            entry.put("Snapshot", SoulSnapshots.sanitize(snapshot));
        }
    }
    public PlayerSouls player(UUID player) { return players.computeIfAbsent(player, id -> new PlayerSouls()); }
    public static SoulLedger load(CompoundTag tag) {
        SoulLedger data = new SoulLedger();
        CompoundTag list = tag.getCompoundOrEmpty("Players");
        list.keySet().forEach(key -> data.players.put(UUID.fromString(key), PlayerSouls.load(list.getCompoundOrEmpty(key))));
        return data;
    }
    public CompoundTag save(CompoundTag tag) {
        CompoundTag list = new CompoundTag();
        players.forEach((id, data) -> list.put(id.toString(), data.save()));
        tag.putInt("Version", 3); tag.put("Players", list);
        return tag;
    }
}
