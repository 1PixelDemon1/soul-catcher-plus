// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.item;

import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

public final class SoulItem extends Item {
    public SoulItem(Properties properties) { super(properties); }

    public static void costTooltip(ItemStack stack, List<ITextComponent> tooltip, int cost) {
        tooltip.add(new TranslationTextComponent(stack.getDescriptionId() + ".cost", cost).withStyle(TextFormatting.AQUA));
    }

    @Override public void appendHoverText(ItemStack stack, World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        costTooltip(stack, tooltip, SACRIFICE_COST);
    }

    public static final int SACRIFICE_COST = 500;
}
