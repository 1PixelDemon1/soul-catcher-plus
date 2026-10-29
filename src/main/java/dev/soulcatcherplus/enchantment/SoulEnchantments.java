// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.enchantment;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScythe;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SoulEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SoulCatcherPlus.ID);
    private static final EnchantmentCategory CAGES =
        EnchantmentCategory.create("SOULCATCHERPLUS_CAGE", item -> item instanceof SoulCage);
    private static final EnchantmentCategory SCYTHES =
        EnchantmentCategory.create("SOULCATCHERPLUS_SCYTHE", item -> item instanceof SoulScythe);
    public static final RegistryObject<Enchantment> SOUL_CAPACITY =
        ENCHANTMENTS.register("soul_capacity", () -> new SoulEnchantment(CAGES, 3));
    public static final RegistryObject<Enchantment> SOUL_HARVESTER =
        ENCHANTMENTS.register("soul_harvester", () -> new SoulEnchantment(SCYTHES, 2));

    /** Clamp command-added levels to the supported range as well. */
    public static int level(ItemStack stack, Enchantment enchantment) {
        return Math.max(0, Math.min(enchantment.getMaxLevel(),
            EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack)));
    }

    /** Only held scythes count; two scythes use the stronger bonus without stacking. */
    public static int victoryEnergy(Player player, int baseEnergy) {
        int level = Math.max(harvesterLevel(player.getMainHandItem()), harvesterLevel(player.getOffhandItem()));
        // Integer rewards round down; use long arithmetic before saturating to the economy's int limit.
        return (int)Math.min(Integer.MAX_VALUE, (long)baseEnergy * (2 + level) / 2);
    }
    private static int harvesterLevel(ItemStack stack) {
        return stack.getItem() instanceof SoulScythe ? level(stack, SOUL_HARVESTER.get()) : 0;
    }

    /** Both enchantments are available from normal enchanting and enchanted books. */
    private static final class SoulEnchantment extends Enchantment {
        private final int maxLevel;
        private SoulEnchantment(EnchantmentCategory category, int maxLevel) {
            super(Rarity.UNCOMMON, category, new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
            this.maxLevel = maxLevel;
        }
        @Override public int getMaxLevel() { return maxLevel; }
        @Override public int getMinCost(int level) { return 10 + (level - 1) * 10; }
        @Override public int getMaxCost(int level) { return getMinCost(level) + 20; }
    }

    private SoulEnchantments() {}
}
