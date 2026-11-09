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
    /** Called after SoulService has recalled this entry and its inherited minions. */
    public boolean forget(UUID id) {
        SoulEntry removed = entries.remove(id);
        if (removed == null) return false;
        if (entries.values().stream().noneMatch(entry -> entry.entityType.equals(removed.entityType)))
            favoriteTypes.remove(removed.entityType);
        if (selected != null && !entries.containsKey(selected))
            selected = entries.isEmpty() ? null : entries.keySet().iterator().next();
        return true;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("CommandMode", commandMode);
        tag.putBoolean("Group", commandGroup);
        if (selected != null) dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Selected", selected);
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
        data.commandMode = tag.getBooleanOr("CommandMode", false);
        data.commandGroup = tag.getBooleanOr("Group", false);
        if (dev.soulcatcherplus.data.SoulNbt.hasUuid(tag, "Selected")) data.selected = dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Selected");
        Set<String> ownedTypes = new HashSet<>();
        for (Tag raw : tag.getListOrEmpty("Souls")) {
            SoulEntry entry = SoulEntry.load((CompoundTag) raw);
            data.entries.put(entry.id, entry);
            ownedTypes.add(entry.entityType);
        }
        for (Tag raw : tag.getListOrEmpty("Favorites"))
            if (ownedTypes.contains(raw.asString().orElse(""))) data.favoriteTypes.add(raw.asString().orElse(""));
        return data;
    }
}
