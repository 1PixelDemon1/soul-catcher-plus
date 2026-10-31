// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import dev.soulcatcherplus.api.SoulAdapters;
import java.util.List;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

/** Appearance-only defaults. Custom adapters can describe their own saved forms. */
public final class SoulVariants {
    private static final List<String> APPEARANCE_KEYS = com.google.common.collect.ImmutableList.of(
        "Variant", "variant", "Color", "color", "CatType", "RabbitType", "Type", "Size",
        "VillagerData", "Skin", "skin", "Texture", "texture", "IsBaby", "Sheared",
        "CollarColor", "Sitting", "Angry", "PuffState", "MainGene", "HiddenGene", "powered");

    public static CompoundNBT appearance(CompoundNBT snapshot) {
        CompoundNBT result = new CompoundNBT();
        for (String key : APPEARANCE_KEYS) {
            if (snapshot.contains(key)) result.put(key, snapshot.get(key).copy());
        }
        // Breeding cooldowns and the exact age are not new appearances.
        if (snapshot.contains("Age")) result.putInt("Age", snapshot.getInt("Age") < 0 ? -24000 : 0);
        return result;
    }

    public static CompoundNBT identity(CompoundNBT snapshot) {
        CompoundNBT result = appearance(snapshot);
        // Temporary pose, anger, puffing and shearing do not unlock extra souls.
        for (String key : com.google.common.collect.ImmutableList.of("Sheared", "Sitting", "Angry", "PuffState", "CollarColor")) result.remove(key);
        if (snapshot.contains("Age")) {
            result.remove("Age");
            result.putBoolean("IsBaby", snapshot.getInt("Age") < 0);
        }
        return result;
    }

    public static CompoundNBT preview(CompoundNBT snapshot) {
        CompoundNBT result = appearance(snapshot);
        // Equipment is visible in the preview, but does not create a new variant.
        for (String key : com.google.common.collect.ImmutableList.of("HandItems", "ArmorItems", "Saddle", "DecorItem", "ArmorItem", "carriedBlockState")) {
            if (snapshot.contains(key)) result.put(key, snapshot.get(key).copy());
        }
        return result;
    }

    public static boolean same(SoulEntry first, SoulEntry second) {
        if (!first.entityType.equals(second.entityType)) return false;
        var adapter = SoulAdapters.get(new ResourceLocation(first.entityType));
        return adapter.variantData(first.snapshot()).equals(adapter.variantData(second.snapshot()));
    }

    private SoulVariants() {}
}
