// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.WorldSavedData;

/** Canonical ownership and summon state, stored in the overworld across dimensions. */
public final class SoulLedger extends WorldSavedData {
    public SoulLedger() { super("soulcatcherplus"); }
    private final Map<UUID, PlayerSouls> players = new HashMap<>();
    public static SoulLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SoulLedger::new, "soulcatcherplus");
    }
    public PlayerSouls player(UUID player) { return players.computeIfAbsent(player, id -> new PlayerSouls()); }
    @Override public void load(CompoundNBT tag) {
        players.clear();
        CompoundNBT list = tag.getCompound("Players");
        list.getAllKeys().forEach(key -> players.put(UUID.fromString(key), PlayerSouls.load(list.getCompound(key))));
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        CompoundNBT list = new CompoundNBT();
        players.forEach((id, data) -> list.put(id.toString(), data.save()));
        tag.putInt("Version", 3); tag.put("Players", list);
        return tag;
    }
}
