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
    private final Map<UUID, dev.soulcatcherplus.pylon.PylonBond> pylons = new HashMap<>();
    public static SoulLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SoulLedger::load, SoulLedger::new, "soulcatcherplus");
    }
    public PlayerSouls player(UUID player) { return players.computeIfAbsent(player, id -> new PlayerSouls()); }
    public dev.soulcatcherplus.pylon.PylonBond pylon(UUID id) { return pylons.get(id); }
    public void putPylon(dev.soulcatcherplus.pylon.PylonBond bond) { pylons.put(bond.soul.id, bond); setDirty(); }
    public dev.soulcatcherplus.pylon.PylonBond removePylon(UUID id) {
        var removed = pylons.remove(id);
        if (removed != null) setDirty();
        return removed;
    }
    public static SoulLedger load(CompoundTag tag) {
        SoulLedger data = new SoulLedger();
        CompoundTag list = tag.getCompound("Players");
        list.getAllKeys().forEach(key -> data.players.put(UUID.fromString(key), PlayerSouls.load(list.getCompound(key))));
        CompoundTag savedPylons = tag.getCompound("Pylons");
        for (String key : savedPylons.getAllKeys()) {
            try {
                var bond = dev.soulcatcherplus.pylon.PylonBond.load(savedPylons.getCompound(key));
                if (bond.soul.id.equals(UUID.fromString(key))) data.pylons.put(bond.soul.id, bond);
            } catch (IllegalArgumentException invalid) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Ignoring invalid soul pylon reservation {}", key, invalid);
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag list = new CompoundTag();
        players.forEach((id, data) -> list.put(id.toString(), data.save()));
        CompoundTag savedPylons = new CompoundTag();
        pylons.forEach((id, bond) -> savedPylons.put(id.toString(), bond.save()));
        tag.put("Pylons", savedPylons);
        tag.putInt("Version", 4); tag.put("Players", list);
        return tag;
    }
}
