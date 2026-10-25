// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PlayerSouls {
    public int energy;
    public UUID selected;
    public boolean commandMode;
    public boolean commandGroup = true;
    public final Map<UUID, SoulEntry> entries = new LinkedHashMap<>();
    public int usedSlots() {
        return entries.values().stream().filter(e -> e.activeEntity != null).mapToInt(e -> e.slots).sum();
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", energy); tag.putBoolean("CommandMode", commandMode);
        tag.putBoolean("Group", commandGroup);
        if (selected != null) tag.putUUID("Selected", selected);
        ListTag list = new ListTag();
        entries.values().forEach(entry -> list.add(entry.save()));
        tag.put("Souls", list);
        return tag;
    }
    public static PlayerSouls load(CompoundTag tag) {
        PlayerSouls data = new PlayerSouls();
        data.energy = Math.max(0, tag.getInt("Energy")); data.commandMode = tag.getBoolean("CommandMode");
        data.commandGroup = tag.getBoolean("Group");
        if (tag.hasUUID("Selected")) data.selected = tag.getUUID("Selected");
        for (Tag raw : tag.getList("Souls", Tag.TAG_COMPOUND)) {
            SoulEntry entry = SoulEntry.load((CompoundTag) raw);
            data.entries.put(entry.id, entry);
        }
        return data;
    }
}
