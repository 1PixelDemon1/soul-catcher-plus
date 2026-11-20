// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.pylon;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulPylons {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    public static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, SoulCatcherPlus.ID);
    public static final DeferredRegister<ContainerType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> PYLON = BLOCKS.register("soul_pylon", SoulPylonBlock::new);
    public static final RegistryObject<Item> ITEM = SoulCatcherPlus.ITEMS.register("soul_pylon",
        () -> new BlockItem(PYLON.get(), new Item.Properties().tab(SoulCatcherPlus.SOUL_TAB)));
    public static final RegistryObject<TileEntityType<SoulPylonTile>> TILE = TILES.register("soul_pylon",
        () -> TileEntityType.Builder.of(SoulPylonTile::new, PYLON.get()).build(null));
    public static final RegistryObject<ContainerType<SoulPylonContainer>> MENU = MENUS.register("soul_pylon",
        () -> net.minecraftforge.common.extensions.IForgeContainerType.create(SoulPylonContainer::new));
    private SoulPylons() {}
}
