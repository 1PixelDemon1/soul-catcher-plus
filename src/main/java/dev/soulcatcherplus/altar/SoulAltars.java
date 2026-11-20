// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.altar;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulAltars {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SoulCatcherPlus.ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("soul_transfer_altar", () -> new SoulAltarBlock(BLOCKS.key("soul_transfer_altar")));
    public static final RegistryObject<Item> ITEM = SoulCatcherPlus.ITEMS.register("soul_transfer_altar",
        () -> new BlockItem(BLOCK.get(), new Item.Properties().setId(SoulCatcherPlus.ITEMS.key(BLOCK.getId().getPath()))));
    public static final RegistryObject<BlockEntityType<SoulAltarTile>> TILE = TILES.register("soul_transfer_altar",
        () -> new BlockEntityType<>(SoulAltarTile::new, java.util.Set.of(BLOCK.get())));
    public static final RegistryObject<MenuType<SoulAltarMenu>> MENU = MENUS.register("soul_transfer_altar",
        () -> IForgeMenuType.create(SoulAltarMenu::new));

    public static void register(BusGroup bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        MENUS.register(bus);
    }
    private SoulAltars() {}
}
