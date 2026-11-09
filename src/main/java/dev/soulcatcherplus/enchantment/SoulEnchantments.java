// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.enchantment;
import dev.soulcatcherplus.item.SoulScythe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class SoulEnchantments {
    public record Key(ResourceKey<Enchantment> value, int maximum) { public ResourceKey<Enchantment> get() { return value; } }
    private static Key key(String path, int maximum) { return new Key(ResourceKey.create(Registries.ENCHANTMENT,Identifier.fromNamespaceAndPath("soulcatcherplus",path)),maximum); }
    public static final Key SOUL_CAPACITY=key("soul_capacity",3), SOUL_HARVESTER=key("soul_harvester",2), SOUL_SLOT=key("soul_slot",1);
    public static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        int maximum=key.equals(SOUL_CAPACITY.get())?3:key.equals(SOUL_HARVESTER.get())?2:1;
        for (var enchantment : stack.getEnchantments().entrySet())
            if (enchantment.getKey().is(key)) return Math.max(0,Math.min(maximum,enchantment.getIntValue()));
        return 0;
    }
    public static int victoryEnergy(Player player, int base) {
        int bonus=Math.max(harvesterLevel(player.getMainHandItem()),harvesterLevel(player.getOffhandItem()));
        return (int)Math.min(Integer.MAX_VALUE,(long)base*(2+bonus)/2);
    }
    private static int harvesterLevel(ItemStack stack) { return stack.getItem() instanceof SoulScythe?level(stack,SOUL_HARVESTER.get()):0; }
    public static int additionalSlots(Player player) {
        int slots=0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) slots+=level(player.getItemBySlot(slot),SOUL_SLOT.get());
        return slots;
    }
    private SoulEnchantments() {}
}
