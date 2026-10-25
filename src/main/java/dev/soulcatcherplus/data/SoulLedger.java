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
    public static SoulLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SoulLedger::load, SoulLedger::new, "soulcatcherplus");
    }
    public PlayerSouls player(UUID player) { return players.computeIfAbsent(player, id -> new PlayerSouls()); }
    public static SoulLedger load(CompoundTag tag) {
        SoulLedger data = new SoulLedger();
        CompoundTag list = tag.getCompound("Players");
        list.getAllKeys().forEach(key -> data.players.put(UUID.fromString(key), PlayerSouls.load(list.getCompound(key))));
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag list = new CompoundTag();
        players.forEach((id, data) -> list.put(id.toString(), data.save()));
        tag.putInt("Version", 1); tag.put("Players", list);
        return tag;
    }
}
