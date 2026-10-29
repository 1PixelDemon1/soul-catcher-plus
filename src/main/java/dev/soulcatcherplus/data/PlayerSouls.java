// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.nbt.INBT;

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
    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putBoolean("CommandMode", commandMode);
        tag.putBoolean("Group", commandGroup);
        if (selected != null) tag.putUUID("Selected", selected);
        ListNBT list = new ListNBT();
        entries.values().forEach(entry -> list.add(entry.save()));
        tag.put("Souls", list);
        ListNBT favorites = new ListNBT();
        favoriteTypes.stream().sorted().forEach(type -> favorites.add(StringNBT.valueOf(type)));
        tag.put("Favorites", favorites);
        return tag;
    }
    public static PlayerSouls load(CompoundNBT tag) {
        PlayerSouls data = new PlayerSouls();
        data.commandMode = tag.getBoolean("CommandMode");
        data.commandGroup = tag.getBoolean("Group");
        if (tag.hasUUID("Selected")) data.selected = tag.getUUID("Selected");
        Set<String> ownedTypes = new HashSet<>();
        for (INBT raw : tag.getList("Souls", 10)) {
            SoulEntry entry = SoulEntry.load((CompoundNBT) raw);
            data.entries.put(entry.id, entry);
            ownedTypes.add(entry.entityType);
        }
        for (INBT raw : tag.getList("Favorites", net.minecraftforge.common.util.Constants.NBT.TAG_STRING))
            if (ownedTypes.contains(raw.getAsString())) data.favoriteTypes.add(raw.getAsString());
        return data;
    }
}
