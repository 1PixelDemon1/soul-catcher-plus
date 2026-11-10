// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class SoulEntry {
    public final UUID id;
    public final String entityType;
    public final String name;
    private final CompoundTag snapshot;
    public final double maxHealth;
    public final int cost;
    public UUID activeEntity;
    public String dimension = "";
    public long readyAt;

    public SoulEntry(UUID id, String type, String name, CompoundTag snapshot, double maxHealth) {
        this.id = id; this.entityType = type; this.name = name;
        this.snapshot = snapshot.copy(); this.maxHealth = SoulBalance.health(maxHealth);
        this.cost = SoulBalance.summonCost(this.maxHealth);
    }
    public boolean hasCustomName() { return snapshot.contains("CustomName"); }
    public CompoundTag snapshot() { return snapshot.copy(); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putString("Type", entityType); tag.putString("Name", name);
        tag.put("Snapshot", snapshot.copy()); tag.putDouble("MaxHealth", maxHealth); tag.putInt("Cost", cost);
        if (activeEntity != null) tag.putUUID("Active", activeEntity);
        tag.putString("Dimension", dimension); tag.putLong("ReadyAt", readyAt);
        return tag;
    }
    public static SoulEntry load(CompoundTag tag) {
        String type = tag.getString("Type");
        double maxHealth = tag.contains("MaxHealth") ? tag.getDouble("MaxHealth")
            : SoulBalance.legacyHealth(tag.getCompound("Snapshot"), tag.getInt("Cost"));
        // Rebalance the built-in courier prototype without replacing its collection or summon identity.
        if (type.equals("soulcatcherplus:soul_courier") && maxHealth == 20)
            maxHealth = dev.soulcatcherplus.entity.SoulCourierEntity.MAX_HEALTH;
        SoulEntry entry = new SoulEntry(tag.getUUID("Id"), type,
            tag.getString("Name"), tag.getCompound("Snapshot"), maxHealth);
        if (tag.hasUUID("Active")) entry.activeEntity = tag.getUUID("Active");
        entry.dimension = tag.getString("Dimension"); entry.readyAt = tag.getLong("ReadyAt");
        return entry;
    }
}
