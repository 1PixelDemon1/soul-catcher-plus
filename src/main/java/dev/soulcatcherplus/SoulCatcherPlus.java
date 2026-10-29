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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityClassification;
import net.minecraft.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

@Mod(SoulCatcherPlus.ID)
public final class SoulCatcherPlus {
    public static final String ID = "soulcatcherplus";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, ID);
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final RegistryObject<Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON));
    public static final RegistryObject<Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND));
    public static final RegistryObject<Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE));
    public static final RegistryObject<Item> IRON_CAGE = ITEMS.register("iron_soul_cage", () -> new SoulCage(150, new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT)));
    public static final RegistryObject<Item> DIAMOND_CAGE = ITEMS.register("diamond_soul_cage", () -> new SoulCage(400, new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT)));
    public static final RegistryObject<Item> NETHERITE_CAGE = ITEMS.register("netherite_soul_cage", () -> new SoulCage(1000, new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT).fireResistant()));
    // Temporary capacity increase for the 1.16.5 playtest.
    public static final RegistryObject<Item> SUPREME_CAGE = ITEMS.register("supreme_soul_cage", () -> new SoulCage(100_000, new Item.Properties().tab(net.minecraft.item.ItemGroup.TAB_COMBAT)));
    /** Compatibility alias for the original iron scythe. */
    public static final RegistryObject<Item> SCYTHE = IRON_SCYTHE;
    public static final RegistryObject<EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, EntityClassification.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ID + ":soul_remnant"));

    public SoulCatcherPlus() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SoulNetwork.register();
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
            event.enqueueWork(() -> {
                if (ModList.get().isLoaded("mowziesmobs")) {
                    SoulAdapters.register(new net.minecraft.util.ResourceLocation("mowziesmobs", "ferrous_wroughtnaut"),
                        new WroughtnautAdapter());
                }
                if (ModList.get().isLoaded("twilightforest")) TwilightForestCompat.register();
            }));
    }
}
