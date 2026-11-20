// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.block.Block;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulProjectors {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, SoulCatcherPlus.ID);
    public static final DeferredRegister<ContainerType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> DARK = BLOCKS.register("soul_projector", SoulProjectorBlock::new);
    public static final RegistryObject<Block> LIGHT = BLOCKS.register("quartz_soul_projector", SoulProjectorBlock::new);
    public static final RegistryObject<Item> DARK_ITEM = SoulCatcherPlus.ITEMS.register("soul_projector",
        () -> new BlockItem(DARK.get(), new Item.Properties().tab(SoulCatcherPlus.SOUL_TAB)));
    public static final RegistryObject<Item> LIGHT_ITEM = SoulCatcherPlus.ITEMS.register("quartz_soul_projector",
        () -> new BlockItem(LIGHT.get(), new Item.Properties().tab(SoulCatcherPlus.SOUL_TAB)));
    public static final RegistryObject<TileEntityType<SoulProjectorTile>> TILE = TILES.register("soul_projector",
        () -> TileEntityType.Builder.of(SoulProjectorTile::new, DARK.get(), LIGHT.get()).build(null));
    public static final RegistryObject<ContainerType<SoulProjectorContainer>> MENU = MENUS.register("soul_projector",
        () -> net.minecraftforge.common.extensions.IForgeContainerType.create(SoulProjectorContainer::new));
    private SoulProjectors() {}
}
