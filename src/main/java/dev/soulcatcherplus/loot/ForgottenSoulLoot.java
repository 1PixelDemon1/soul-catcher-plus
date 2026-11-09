// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.soulcatcherplus.SoulCatcherPlus;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Additive single roll, restricted to root chest loot tables. */
public final class ForgottenSoulLoot extends LootModifier {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SoulCatcherPlus.ID);
    public static final RegistryObject<MapCodec<ForgottenSoulLoot>> FORGOTTEN_SOULS = SERIALIZERS.register("forgotten_souls", () -> RecordCodecBuilder.mapCodec(instance -> codecStart(instance).and(Codec.floatRange(0,1).fieldOf("chance").forGetter(value -> value.chance)).apply(instance,ForgottenSoulLoot::new)));
    private final float chance;
    private ForgottenSoulLoot(LootItemCondition[] conditions, float chance) { super(conditions); this.chance=chance; }
    @Override protected ObjectArrayList<ItemStack> doApply(net.minecraft.world.level.storage.loot.LootTable table, ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getQueriedLootTableId().getPath().startsWith("chests/") && context.getRandom().nextFloat() < chance)
            loot.add(SoulCatcherPlus.FORGOTTEN_SOUL_SCROLL.get().randomPreset(context.getRandom()));
        return loot;
    }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return FORGOTTEN_SOULS.get(); }
}
