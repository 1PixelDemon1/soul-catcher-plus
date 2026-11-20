// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulPylons {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SoulCatcherPlus.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> PYLON = BLOCKS.register("soul_pylon", SoulPylonBlock::new);
    public static final RegistryObject<Item> ITEM = SoulCatcherPlus.ITEMS.register("soul_pylon",
        () -> new BlockItem(PYLON.get(), new Item.Properties().tab(SoulCatcherPlus.SOUL_TAB)));
    public static final RegistryObject<BlockEntityType<SoulPylonTile>> TILE = TILES.register("soul_pylon",
        () -> BlockEntityType.Builder.of(SoulPylonTile::new, PYLON.get()).build(null));
    public static final RegistryObject<MenuType<SoulPylonContainer>> MENU = MENUS.register("soul_pylon",
        () -> net.minecraftforge.common.extensions.IForgeMenuType.create(SoulPylonContainer::new));
    private SoulPylons() {}
}
