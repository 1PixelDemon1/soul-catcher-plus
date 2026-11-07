// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import dev.soulcatcherplus.SoulCatcherPlus;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.conditions.ILootCondition;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.loot.GlobalLootModifierSerializer;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Additive, one-roll reward for root chest loot tables, including standard modded chest paths. */
public final class ForgottenSoulLoot extends LootModifier {
    public static final DeferredRegister<GlobalLootModifierSerializer<?>> SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.LOOT_MODIFIER_SERIALIZERS, SoulCatcherPlus.ID);
    public static final RegistryObject<Serializer> FORGOTTEN_SOULS = SERIALIZERS.register("forgotten_souls", Serializer::new);
    private final float chance;

    private ForgottenSoulLoot(ILootCondition[] conditions, float chance) {
        super(conditions);
        if (!Float.isFinite(chance) || chance < 0 || chance > 1)
            throw new JsonSyntaxException("Forgotten soul chest chance must be between 0 and 1");
        this.chance = chance;
    }

    @Override protected List<ItemStack> doApply(List<ItemStack> loot, LootContext context) {
        if (context.getQueriedLootTableId().getPath().startsWith("chests/")
                && context.getRandom().nextFloat() < chance)
            loot.add(SoulCatcherPlus.FORGOTTEN_SOUL_SCROLL.get().randomPreset(context.getRandom()));
        return loot;
    }

    public static final class Serializer extends GlobalLootModifierSerializer<ForgottenSoulLoot> {
        @Override public ForgottenSoulLoot read(ResourceLocation id, JsonObject json, ILootCondition[] conditions) {
            return new ForgottenSoulLoot(conditions, JSONUtils.getAsFloat(json, "chance"));
        }

        @Override public JsonObject write(ForgottenSoulLoot modifier) {
            JsonObject json = makeConditions(modifier.conditions);
            json.addProperty("chance", modifier.chance);
            return json;
        }
    }
}
