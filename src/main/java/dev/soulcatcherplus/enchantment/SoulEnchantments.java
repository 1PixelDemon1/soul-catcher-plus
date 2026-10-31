// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.enchantment;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScythe;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
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
    public static final RegistryObject<Enchantment> SOUL_SLOT =
        ENCHANTMENTS.register("soul_slot", SoulSlotEnchantment::new);

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

    /** Only the four worn armor pieces count, once per piece regardless of command-added level. */
    public static int additionalSlots(Player player) {
        int slots = 0;
        for (ItemStack armor : player.getArmorSlots())
            if (armor.getItem() instanceof ArmorItem) slots += level(armor, SOUL_SLOT.get());
        return slots;
    }

    private static final class SoulSlotEnchantment extends Enchantment {
        private SoulSlotEnchantment() {
            super(Rarity.RARE, EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
        }
        @Override public int getMaxLevel() { return 1; }
        // Match the enchanting-power window of vanilla Looting II, not its three-level progression.
        @Override public int getMinCost(int level) { return Enchantments.MOB_LOOTING.getMinCost(2); }
        @Override public int getMaxCost(int level) { return Enchantments.MOB_LOOTING.getMaxCost(2); }
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
