// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.*;

/** Version-specific storage bridge. Preserve UUID int arrays and captured native entity state. */
public final class SoulNbt {
    public static boolean hasUuid(CompoundTag data, String key) { return data.read(key, UUIDUtil.CODEC).isPresent(); }
    public static UUID uuid(CompoundTag data, String key) { return data.read(key, UUIDUtil.CODEC).orElseThrow(() -> new IllegalArgumentException("Missing UUID: "+key)); }
    public static void putUuid(CompoundTag data, String key, UUID value) { data.store(key, UUIDUtil.CODEC, value); }
    public static boolean hasUuid(ValueInput data, String key) { return data.read(key, UUIDUtil.CODEC).isPresent(); }
    public static UUID uuid(ValueInput data, String key) { return data.read(key, UUIDUtil.CODEC).orElseThrow(() -> new IllegalArgumentException("Missing UUID: "+key)); }
    public static void putUuid(ValueOutput data, String key, UUID value) { data.store(key, UUIDUtil.CODEC, value); }
    public static CompoundTag snapshot(Entity entity) {
        TagValueOutput output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }
    public static void restore(Entity entity, CompoundTag snapshot) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING,entity.registryAccess(),snapshot));
    }
    private SoulNbt() {}
}
