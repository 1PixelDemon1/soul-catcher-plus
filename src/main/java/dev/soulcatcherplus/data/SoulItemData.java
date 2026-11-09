// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
/** Custom item payloads use vanilla data components; entity/ledger NBT remains separate. */
public final class SoulItemData {
    public static CompoundTag read(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(); }
    public static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> action) { CustomData.update(DataComponents.CUSTOM_DATA,stack,action); }
    private SoulItemData() {}
}
