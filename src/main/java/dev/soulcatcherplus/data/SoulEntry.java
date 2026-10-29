// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;

public final class SoulEntry {
    public final UUID id;
    public final String entityType;
    public final String name;
    private final CompoundNBT snapshot;
    public final double maxHealth;
    public final int cost;
    public UUID activeEntity;
    public String dimension = "";
    public long readyAt;

    public SoulEntry(UUID id, String type, String name, CompoundNBT snapshot, double maxHealth) {
        this.id = id; this.entityType = type; this.name = name;
        this.snapshot = snapshot.copy(); this.maxHealth = SoulBalance.health(maxHealth);
        this.cost = SoulBalance.summonCost(this.maxHealth);
    }
    public CompoundNBT snapshot() { return snapshot.copy(); }
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putUUID("Id", id); tag.putString("Type", entityType); tag.putString("Name", name);
        tag.put("Snapshot", snapshot.copy()); tag.putDouble("MaxHealth", maxHealth); tag.putInt("Cost", cost);
        if (activeEntity != null) tag.putUUID("Active", activeEntity);
        tag.putString("Dimension", dimension); tag.putLong("ReadyAt", readyAt);
        return tag;
    }
    public static SoulEntry load(CompoundNBT tag) {
        SoulEntry entry = new SoulEntry(tag.getUUID("Id"), tag.getString("Type"),
            tag.getString("Name"), tag.getCompound("Snapshot"), tag.contains("MaxHealth") ? tag.getDouble("MaxHealth")
                : SoulBalance.legacyHealth(tag.getCompound("Snapshot"), tag.getInt("Cost")));
        if (tag.hasUUID("Active")) entry.activeEntity = tag.getUUID("Active");
        entry.dimension = tag.getString("Dimension"); entry.readyAt = tag.getLong("ReadyAt");
        return entry;
    }
}
