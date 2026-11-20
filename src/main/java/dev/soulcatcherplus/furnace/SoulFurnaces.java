// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.furnace;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class SoulFurnaces {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SoulCatcherPlus.ID);
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SoulCatcherPlus.ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SoulCatcherPlus.ID);
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("soul_fueled_furnace", SoulFurnaceBlock::new);
    public static final RegistryObject<Item> ITEM = SoulCatcherPlus.ITEMS.register("soul_fueled_furnace",
        () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SoulFurnaceTile>> TILE = TILES.register("soul_fueled_furnace",
        () -> BlockEntityType.Builder.of(SoulFurnaceTile::new, BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<SoulFurnaceMenu>> MENU = MENUS.register("soul_fueled_furnace",
        () -> IForgeMenuType.create(SoulFurnaceMenu::new));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        MENUS.register(bus);
    }
    private SoulFurnaces() {}
}
