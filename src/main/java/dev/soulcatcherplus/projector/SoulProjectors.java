// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulProjectors {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SoulCatcherPlus.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> DARK = BLOCKS.register("soul_projector", SoulProjectorBlock::new);
    public static final RegistryObject<Block> LIGHT = BLOCKS.register("quartz_soul_projector", SoulProjectorBlock::new);
    public static final RegistryObject<Item> DARK_ITEM = SoulCatcherPlus.ITEMS.register("soul_projector",
        () -> new BlockItem(DARK.get(), new Item.Properties()));
    public static final RegistryObject<Item> LIGHT_ITEM = SoulCatcherPlus.ITEMS.register("quartz_soul_projector",
        () -> new BlockItem(LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SoulProjectorTile>> TILE = TILES.register("soul_projector",
        () -> BlockEntityType.Builder.of(SoulProjectorTile::new, DARK.get(), LIGHT.get()).build(null));
    public static final RegistryObject<MenuType<SoulProjectorContainer>> MENU = MENUS.register("soul_projector",
        () -> net.minecraftforge.common.extensions.IForgeMenuType.create(SoulProjectorContainer::new));
    private SoulProjectors() {}
}
