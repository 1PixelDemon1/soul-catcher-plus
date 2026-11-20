// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.FloatNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.INBT;

/** Preserve mod-specific saved state, but never restore old identity/death/ownership. */
public final class SoulSnapshots {
    /** Bounded client-safe appearance, shared by collection previews and decorative projectors. */
    public static CompoundNBT preview(SoulEntry entry) throws java.io.IOException {
        CompoundNBT visual = sanitize(dev.soulcatcherplus.api.SoulAdapters.get(
            new net.minecraft.util.ResourceLocation(entry.entityType)).previewData(entry.snapshot()));
        visual.remove("ForgeData"); visual.remove("ForgeCaps");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.CompressedStreamTools.write(visual, new java.io.DataOutputStream(bytes));
        return bytes.size() <= 16 * 1024 ? visual : new CompoundNBT();
    }
    public static CompoundNBT sanitize(CompoundNBT original) {
        CompoundNBT tag = original.copy();
        for (String key : new String[]{"UUID", "Pos", "Motion", "Rotation", "Passengers", "Leash",
                "Brain", "DeathTime", "HurtTime", "HurtByTimestamp", "Health", "Fire",
                "FallDistance", "PortalCooldown", "SleepingX", "SleepingY", "SleepingZ"})
            tag.remove(key);
        CompoundNBT forge = tag.getCompound("ForgeData");
        forge.getAllKeys().stream().filter(k -> k.startsWith("scp_")).collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(forge::remove);
        if (!forge.isEmpty()) tag.put("ForgeData", forge);
        tag.putBoolean("PersistenceRequired", true);
        tag.putBoolean("CanPickUpLoot", false);
        tag.putBoolean("NoAI", false);
        tag.putShort("DeathTime", (short) 0);
        tag.putShort("HurtTime", (short) 0);
        for (String key : new String[]{"HandDropChances", "ArmorDropChances"}) {
            ListNBT drops = new ListNBT();
            int size = key.startsWith("Hand") ? 2 : 4;
            for (int i = 0; i < size; i++) drops.add(FloatNBT.valueOf(0));
            tag.put(key, drops);
        }
        // Temporary potion effects must not survive as permanent collection bonuses.
        tag.remove("ActiveEffects");
        return tag;
    }
    private SoulSnapshots() {}
}
