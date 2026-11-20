// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.block.Block;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulAltars {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    private static final DeferredRegister<TileEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, SoulCatcherPlus.ID);
    private static final DeferredRegister<ContainerType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("soul_transfer_altar", SoulAltarBlock::new);
    public static final RegistryObject<Item> ITEM = SoulCatcherPlus.ITEMS.register("soul_transfer_altar",
        () -> new BlockItem(BLOCK.get(), new Item.Properties().tab(SoulCatcherPlus.SOUL_TAB)));
    public static final RegistryObject<TileEntityType<SoulAltarTile>> TILE = TILES.register("soul_transfer_altar",
        () -> TileEntityType.Builder.of(SoulAltarTile::new, BLOCK.get()).build(null));
    public static final RegistryObject<ContainerType<SoulAltarMenu>> MENU = MENUS.register("soul_transfer_altar",
        () -> IForgeContainerType.create(SoulAltarMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        MENUS.register(bus);
    }
    private SoulAltars() {}
}
