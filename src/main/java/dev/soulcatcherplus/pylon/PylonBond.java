// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.data.SoulEntry;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

/** A world-saved reservation, independent of the owner's collection and loaded chunks. */
public final class PylonBond {
    public final UUID owner;
    public final String dimension;
    public final BlockPos position;
    public final SoulEntry soul;

    public PylonBond(UUID owner, String dimension, BlockPos position, SoulEntry soul) {
        this.owner = owner;
        this.dimension = dimension;
        this.position = position.immutable();
        this.soul = soul;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        dev.soulcatcherplus.data.SoulNbt.putUuid(tag, "Owner", owner);
        tag.putString("Dimension", dimension);
        tag.putLong("Position", position.asLong());
        tag.put("Soul", soul.save());
        return tag;
    }

    public static PylonBond load(CompoundTag tag) {
        return new PylonBond(dev.soulcatcherplus.data.SoulNbt.uuid(tag, "Owner"), tag.getStringOr("Dimension", ""),
            BlockPos.of(tag.getLongOr("Position", 0)), SoulEntry.load(tag.getCompoundOrEmpty("Soul")));
    }
}
