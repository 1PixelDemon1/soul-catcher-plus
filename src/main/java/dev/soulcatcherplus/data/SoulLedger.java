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
    private final Map<UUID, dev.soulcatcherplus.pylon.PylonBond> pylons = new HashMap<>();
    public static SoulLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SoulLedger::new, "soulcatcherplus");
    }
    public PlayerSouls player(UUID player) { return players.computeIfAbsent(player, id -> new PlayerSouls()); }
    public dev.soulcatcherplus.pylon.PylonBond pylon(UUID id) { return pylons.get(id); }
    public void putPylon(dev.soulcatcherplus.pylon.PylonBond bond) { pylons.put(bond.soul.id, bond); setDirty(); }
    public dev.soulcatcherplus.pylon.PylonBond removePylon(UUID id) {
        var removed = pylons.remove(id);
        if (removed != null) setDirty();
        return removed;
    }
    @Override public void load(CompoundNBT tag) {
        players.clear();
        pylons.clear();
        CompoundNBT list = tag.getCompound("Players");
        list.getAllKeys().forEach(key -> players.put(UUID.fromString(key), PlayerSouls.load(list.getCompound(key))));
        CompoundNBT savedPylons = tag.getCompound("Pylons");
        for (String key : savedPylons.getAllKeys()) {
            try {
                var bond = dev.soulcatcherplus.pylon.PylonBond.load(savedPylons.getCompound(key));
                if (bond.soul.id.equals(UUID.fromString(key))) pylons.put(bond.soul.id, bond);
            } catch (IllegalArgumentException invalid) {
                org.apache.logging.log4j.LogManager.getLogger().warn("Ignoring invalid soul pylon reservation {}", key, invalid);
            }
        }
    }
    @Override public CompoundNBT save(CompoundNBT tag) {
        CompoundNBT list = new CompoundNBT();
        players.forEach((id, data) -> list.put(id.toString(), data.save()));
        CompoundNBT savedPylons = new CompoundNBT();
        pylons.forEach((id, bond) -> savedPylons.put(id.toString(), bond.save()));
        tag.put("Pylons", savedPylons);
        tag.putInt("Version", 4); tag.put("Players", list);
        return tag;
    }
}
