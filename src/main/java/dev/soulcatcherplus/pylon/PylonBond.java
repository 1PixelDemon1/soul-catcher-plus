// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.data.SoulEntry;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;

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

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        tag.putUUID("Owner", owner);
        tag.putString("Dimension", dimension);
        tag.putLong("Position", position.asLong());
        tag.put("Soul", soul.save());
        return tag;
    }

    public static PylonBond load(CompoundNBT tag) {
        return new PylonBond(tag.getUUID("Owner"), tag.getString("Dimension"),
            BlockPos.of(tag.getLong("Position")), SoulEntry.load(tag.getCompound("Soul")));
    }
}
