// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.enchantment.SoulEnchantments;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.ScytheTier;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(SoulCatcherPlus.ID)
public final class SoulCatcherPlus {
    public static final String ID = "soulcatcherplus";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final CreativeModeTab SOUL_TAB = new CreativeModeTab(ID) {
        @Override public ItemStack makeIcon() { return new ItemStack(SoulCatcherPlus.SOUL_ICON.get()); }
    };
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final RegistryObject<Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON));
    public static final RegistryObject<Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND));
    public static final RegistryObject<Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE));
    public static final RegistryObject<Item> SUPREME_SCYTHE = ITEMS.register("supreme_soul_scythe", () -> new SoulScythe(ScytheTier.SUPREME));
    public static final RegistryObject<Item> IRON_CAGE = ITEMS.register("iron_soul_cage", () -> new SoulCage(150, new Item.Properties().tab(SOUL_TAB)));
    public static final RegistryObject<Item> DIAMOND_CAGE = ITEMS.register("diamond_soul_cage", () -> new SoulCage(400, new Item.Properties().tab(SOUL_TAB)));
    public static final RegistryObject<Item> NETHERITE_CAGE = ITEMS.register("netherite_soul_cage", () -> new SoulCage(1000, new Item.Properties().tab(SOUL_TAB).fireResistant()));
    public static final RegistryObject<Item> SUPREME_CAGE = ITEMS.register("supreme_soul_cage", () -> new SoulCage(2000, new Item.Properties().tab(SOUL_TAB)));
    /** Display-only item for the tab icon; omitted from the tab's obtainable equipment. */
    public static final RegistryObject<Item> SOUL_ICON = ITEMS.register("soul_icon", () -> new Item(new Item.Properties()));
    /** Compatibility alias for the original iron scythe. */
    public static final RegistryObject<Item> SCYTHE = IRON_SCYTHE;
    public static final RegistryObject<EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, MobCategory.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ID + ":soul_remnant"));

    public SoulCatcherPlus() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SoulEnchantments.ENCHANTMENTS.register(bus);
        SoulNetwork.register();
    }
}
