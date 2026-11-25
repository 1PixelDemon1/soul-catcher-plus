// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import dev.soulcatcherplus.entity.ForgottenForm;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class SoulEntry {
    public final UUID id;
    public final String entityType;
    public final String name;
    private CompoundTag snapshot;
    public double maxHealth;
    public int cost;
    public UUID activeEntity;
    public String dimension = "";
    public long readyAt;

    public SoulEntry(UUID id, String type, String name, CompoundTag snapshot, double maxHealth) {
        this.id = id; this.entityType = type; this.name = name;
        this.snapshot = snapshot.copy(); this.maxHealth = SoulBalance.health(maxHealth);
        this.cost = SoulBalance.summonCost(this.maxHealth);
    }
    public ForgottenForm forgottenForm() { return ForgottenForm.from(entityType, snapshot); }

    public Component displayName() {
        ForgottenForm form = forgottenForm();
        return form != null && !hasCustomName() ? Component.translatable(form.nameKey()) : Component.literal(name);
    }

    public boolean hasCustomName() { return snapshot.contains("CustomName"); }
    public CompoundTag snapshot() { return snapshot.copy(); }
    public boolean updateSnapshot(CompoundTag captured, double currentMaxHealth) {
        CompoundTag current = captured.copy();
        // The live name includes a generated owner prefix. Keep the collection name.
        for (String key : new String[]{"CustomName", "CustomNameVisible"}) {
            if (snapshot.contains(key)) current.put(key, snapshot.get(key).copy());
            else current.remove(key);
        }
        double health = SoulBalance.health(currentMaxHealth);
        if (current.equals(snapshot) && health == maxHealth) return false;
        snapshot = current;
        maxHealth = health;
        cost = SoulBalance.summonCost(health);
        return true;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Id", id); tag.putString("Type", entityType); tag.putString("Name", name);
        tag.put("Snapshot", snapshot.copy()); tag.putDouble("MaxHealth", maxHealth); tag.putInt("Cost", cost);
        if (activeEntity != null) dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Active", activeEntity);
        tag.putString("Dimension", dimension); tag.putLong("ReadyAt", readyAt);
        return tag;
    }
    public static SoulEntry load(CompoundTag tag) {
        String type = tag.getStringOr("Type", "");
        double maxHealth = tag.contains("MaxHealth") ? tag.getDoubleOr("MaxHealth", 0d)
            : SoulBalance.legacyHealth(tag.getCompoundOrEmpty("Snapshot"), tag.getIntOr("Cost", 0));
        // Rebalance the built-in courier prototype without replacing its collection or summon identity.
        if (type.equals("soulcatcherplus:soul_courier") && maxHealth == 20)
            maxHealth = dev.soulcatcherplus.entity.SoulCourierEntity.MAX_HEALTH;
        CompoundTag snapshot = tag.getCompoundOrEmpty("Snapshot").copy();
        snapshot.merge(tag.getCompoundOrEmpty("SealedEquipment"));
        SoulEntry entry = new SoulEntry(dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Id"), type,
            tag.getStringOr("Name", ""), snapshot, maxHealth);
        if (dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "Active")) entry.activeEntity = dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Active");
        entry.dimension = tag.getStringOr("Dimension", ""); entry.readyAt = tag.getLongOr("ReadyAt", 0L);
        return entry;
    }
}
