// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.compat.WroughtnautAdapter;
import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.ScytheTier;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
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
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final RegistryObject<Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON));
    public static final RegistryObject<Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND));
    public static final RegistryObject<Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE));
    /** Compatibility alias for the original iron scythe. */
    public static final RegistryObject<Item> SCYTHE = IRON_SCYTHE;
    public static final RegistryObject<EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, MobCategory.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ID + ":soul_remnant"));

    public SoulCatcherPlus(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SoulNetwork.register();
        bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(IRON_SCYTHE); event.accept(DIAMOND_SCYTHE); event.accept(NETHERITE_SCYTHE);
            }
        });
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("mowziesmobs")) {
                    SoulAdapters.register(new net.minecraft.resources.ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"),
                        new WroughtnautAdapter());
                }
            }));
    }
}
