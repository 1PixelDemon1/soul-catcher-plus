// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class SoulEntry {
    public final UUID id;
    public final String entityType;
    public final String name;
    private final CompoundTag snapshot;
    public final int cost;
    public final int slots;
    public UUID activeEntity;
    public String dimension = "";
    public long readyAt;

    public SoulEntry(UUID id, String type, String name, CompoundTag snapshot, int cost, int slots) {
        this.id = id; this.entityType = type; this.name = name;
        this.snapshot = snapshot.copy(); this.cost = Math.max(1, cost); this.slots = Math.max(1, slots);
    }
    public CompoundTag snapshot() { return snapshot.copy(); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putString("Type", entityType); tag.putString("Name", name);
        tag.put("Snapshot", snapshot.copy()); tag.putInt("Cost", cost); tag.putInt("Slots", slots);
        if (activeEntity != null) tag.putUUID("Active", activeEntity);
        tag.putString("Dimension", dimension); tag.putLong("ReadyAt", readyAt);
        return tag;
    }
    public static SoulEntry load(CompoundTag tag) {
        SoulEntry entry = new SoulEntry(tag.getUUID("Id"), tag.getString("Type"),
            tag.getString("Name"), tag.getCompound("Snapshot"), tag.getInt("Cost"), tag.getInt("Slots"));
        if (tag.hasUUID("Active")) entry.activeEntity = tag.getUUID("Active");
        entry.dimension = tag.getString("Dimension"); entry.readyAt = tag.getLong("ReadyAt");
        return entry;
    }
}
