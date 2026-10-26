// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class PlayerSouls {
    public static final int MAX_ENTRIES = 4096;
    public int energy;
    public UUID selected;
    public boolean commandMode;
    public boolean commandGroup = true;
    public final Map<UUID, SoulEntry> entries = new LinkedHashMap<>();
    private final Set<String> favoriteTypes = new HashSet<>();
    public boolean isFavorite(String entityType) { return favoriteTypes.contains(entityType); }
    public void toggleFavorite(UUID entryId) {
        SoulEntry entry = entries.get(entryId);
        if (entry == null) return;
        if (!favoriteTypes.remove(entry.entityType)) favoriteTypes.add(entry.entityType);
    }
    public int activeCount() {
        return (int) entries.values().stream().filter(e -> e.activeEntity != null).count();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", energy); tag.putBoolean("CommandMode", commandMode);
        tag.putBoolean("Group", commandGroup);
        if (selected != null) tag.putUUID("Selected", selected);
        ListTag list = new ListTag();
        entries.values().forEach(entry -> list.add(entry.save()));
        tag.put("Souls", list);
        ListTag favorites = new ListTag();
        favoriteTypes.stream().sorted().forEach(type -> favorites.add(StringTag.valueOf(type)));
        tag.put("Favorites", favorites);
        return tag;
    }
    public static PlayerSouls load(CompoundTag tag) {
        PlayerSouls data = new PlayerSouls();
        data.energy = Math.max(0, tag.getInt("Energy")); data.commandMode = tag.getBoolean("CommandMode");
        data.commandGroup = tag.getBoolean("Group");
        if (tag.hasUUID("Selected")) data.selected = tag.getUUID("Selected");
        Set<String> ownedTypes = new HashSet<>();
        for (Tag raw : tag.getList("Souls", Tag.TAG_COMPOUND)) {
            SoulEntry entry = SoulEntry.load((CompoundTag) raw);
            data.entries.put(entry.id, entry);
            ownedTypes.add(entry.entityType);
        }
        for (Tag raw : tag.getList("Favorites", Tag.TAG_STRING))
            if (ownedTypes.contains(raw.getAsString())) data.favoriteTypes.add(raw.getAsString());
        return data;
    }
}
