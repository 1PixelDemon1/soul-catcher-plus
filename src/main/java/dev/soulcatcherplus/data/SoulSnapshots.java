// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Preserve mod-specific saved state, but never restore old identity/death/ownership. */
public final class SoulSnapshots {
    public static CompoundTag preview(SoulEntry entry) throws java.io.IOException {
        var adapter = dev.soulcatcherplus.api.SoulAdapters.get(net.minecraft.resources.Identifier.parse(entry.entityType));
        CompoundTag tag = sanitize(adapter.previewData(entry.snapshot()));
        tag.remove("ForgeData");
        tag.remove("ForgeCaps");
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        return bytes.size() <= 16 * 1024 ? tag : new CompoundTag();
    }

    public static CompoundTag sanitize(CompoundTag original) {
        CompoundTag tag = original.copy();
        for (String key : new String[]{"UUID", "Pos", "Motion", "Rotation", "Passengers", "Leash",
                "Brain", "DeathTime", "HurtTime", "HurtByTimestamp", "Health", "Fire",
                "FallDistance", "PortalCooldown", "SleepingX", "SleepingY", "SleepingZ", "AbsorptionAmount",
                "fall_distance", "invulnerable_time", "leash", "sleeping_pos", "active_effects",
                "last_hurt_by_player", "last_hurt_by_player_memory_time", "last_hurt_by_mob",
                "ticks_since_last_hurt_by_mob", "current_impulse_context_reset_grace_time", "current_explosion_impact_pos"})
            tag.remove(key);
        CompoundTag forge = tag.getCompoundOrEmpty("ForgeData");
        forge.keySet().stream().filter(k -> k.startsWith("scp_")).collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(forge::remove);
        if (!forge.isEmpty()) tag.put("ForgeData", forge);
        tag.putBoolean("PersistenceRequired", true);
        tag.putBoolean("CanPickUpLoot", false);
        tag.putBoolean("NoAI", false);
        tag.putShort("DeathTime", (short) 0);
        tag.putShort("HurtTime", (short) 0);
        for (String key : new String[]{"HandDropChances", "ArmorDropChances"}) {
            ListTag drops = new ListTag();
            int size = key.startsWith("Hand") ? 2 : 4;
            for (int i = 0; i < size; i++) drops.add(FloatTag.valueOf(0));
            tag.put(key, drops);
        }
        // Temporary potion effects must not survive as permanent collection bonuses.
        tag.remove("ActiveEffects");
        return tag;
    }
    private SoulSnapshots() {}
}
