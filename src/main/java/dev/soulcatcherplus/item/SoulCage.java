// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Soul energy belongs to this non-stackable item, including when traded or upgraded. */
public final class SoulCage extends Item {
    private static final String ENERGY = "SoulEnergy";
    private final int capacity;

    public SoulCage(int capacity, Properties properties) {
        super(properties.stacksTo(1).tab(CreativeModeTab.TAB_COMBAT));
        if (capacity < 1) throw new IllegalArgumentException("Soul cage capacity must be positive");
        this.capacity = capacity;
    }
    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof SoulCage cage ? cage.capacity : 0;
    }
    public static int energy(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, Math.min(capacity(stack), stack.getTag().getInt(ENERGY))) : 0;
    }
    /** Returns the stored amount after clamping to the container's capacity. */
    public static int setEnergy(ItemStack stack, int amount) {
        int capacity = capacity(stack);
        if (capacity == 0) return 0;
        int stored = Math.max(0, Math.min(capacity, amount));
        stack.getOrCreateTag().putInt(ENERGY, stored);
        return stored;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.soulcatcherplus.soul_cage.energy", energy(stack), capacity));
    }
    @Override public boolean isBarVisible(ItemStack stack) { return energy(stack) > 0; }
    @Override public int getBarWidth(ItemStack stack) { return Math.round(13f * energy(stack) / capacity); }
    @Override public int getBarColor(ItemStack stack) { return 0x26D9ED; }
}
