// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.compat.WroughtnautAdapter;
import dev.soulcatcherplus.compat.TwilightForestCompat;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.ScytheTier;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(SoulCatcherPlus.ID)
public final class SoulCatcherPlus {
    public static final String ID = "soulcatcherplus";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ID);
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final DeferredHolder<Item, Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON));
    public static final DeferredHolder<Item, Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND));
    public static final DeferredHolder<Item, Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE));
    public static final DeferredHolder<Item, Item> IRON_CAGE = ITEMS.register("iron_soul_cage", () -> new SoulCage(150, new Item.Properties()));
    public static final DeferredHolder<Item, Item> DIAMOND_CAGE = ITEMS.register("diamond_soul_cage", () -> new SoulCage(400, new Item.Properties()));
    public static final DeferredHolder<Item, Item> NETHERITE_CAGE = ITEMS.register("netherite_soul_cage", () -> new SoulCage(1000, new Item.Properties().fireResistant()));
    public static final DeferredHolder<Item, Item> SUPREME_CAGE = ITEMS.register("supreme_soul_cage", () -> new SoulCage(2000, new Item.Properties()));
    /** Compatibility alias for the original iron scythe. */
    public static final DeferredHolder<Item, Item> SCYTHE = IRON_SCYTHE;
    public static final DeferredHolder<EntityType<?>, EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, MobCategory.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ID + ":soul_remnant"));

    public SoulCatcherPlus(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(SoulNetwork::register);
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(IRON_SCYTHE.get()); event.accept(DIAMOND_SCYTHE.get()); event.accept(NETHERITE_SCYTHE.get());
                event.accept(IRON_CAGE.get()); event.accept(DIAMOND_CAGE.get()); event.accept(NETHERITE_CAGE.get()); event.accept(SUPREME_CAGE.get());
            }
        });
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("mowziesmobs")) {
                    SoulAdapters.register(new net.minecraft.resources.ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"),
                        new WroughtnautAdapter());
                }
                if (ModList.get().isLoaded("twilightforest")) TwilightForestCompat.register();
            }));
    }
}
