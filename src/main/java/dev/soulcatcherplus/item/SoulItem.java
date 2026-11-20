// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import java.util.List;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

public final class SoulItem extends Item {
    public SoulItem(Properties properties) { super(properties); }

    public static void costTooltip(ItemStack stack, java.util.function.Consumer<Component> tooltip, int cost) {
        tooltip.accept(Component.translatable(stack.getItem().getDescriptionId() + ".cost", cost).withStyle(ChatFormatting.AQUA));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        costTooltip(stack, tooltip, SACRIFICE_COST);
    }

    public static final int SACRIFICE_COST = 500;
}
