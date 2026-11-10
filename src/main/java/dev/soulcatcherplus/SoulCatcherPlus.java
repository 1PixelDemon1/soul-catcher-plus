// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.entity.SoulRemnant;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import dev.soulcatcherplus.entity.SoulRavenAdapter;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import dev.soulcatcherplus.entity.SoulCourierAdapter;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import dev.soulcatcherplus.entity.SoulShamanAdapter;
import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.enchantment.SoulEnchantments;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.SoulScroll;
import dev.soulcatcherplus.item.ForgottenSoulScroll;
import dev.soulcatcherplus.loot.ForgottenSoulLoot;
import dev.soulcatcherplus.item.ScytheTier;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.config.SoulClientConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityClassification;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
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
    public static final ItemGroup SOUL_TAB = new ItemGroup(ID) {
        @Override public ItemStack makeIcon() { return new ItemStack(SoulCatcherPlus.SOUL_ICON.get()); }
    };
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final RegistryObject<Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON));
    public static final RegistryObject<Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND));
    public static final RegistryObject<Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE));
    public static final RegistryObject<Item> SUPREME_SCYTHE = ITEMS.register("supreme_soul_scythe", () -> new SoulScythe(ScytheTier.SUPREME));
    public static final RegistryObject<Item> IRON_CAGE = ITEMS.register("iron_soul_cage", () -> new SoulCage(150,
        new Item.Properties().tab(SOUL_TAB).setISTER(() -> dev.soulcatcherplus.client.SoulCageItemRenderer::getInstance)));
    public static final RegistryObject<Item> DIAMOND_CAGE = ITEMS.register("diamond_soul_cage", () -> new SoulCage(400,
        new Item.Properties().tab(SOUL_TAB).setISTER(() -> dev.soulcatcherplus.client.SoulCageItemRenderer::getInstance)));
    public static final RegistryObject<Item> NETHERITE_CAGE = ITEMS.register("netherite_soul_cage", () -> new SoulCage(1000,
        new Item.Properties().tab(SOUL_TAB).fireResistant().setISTER(() -> dev.soulcatcherplus.client.SoulCageItemRenderer::getInstance)));
    public static final RegistryObject<Item> SUPREME_CAGE = ITEMS.register("supreme_soul_cage", () -> new SoulCage(2000,
        new Item.Properties().tab(SOUL_TAB).setISTER(() -> dev.soulcatcherplus.client.SoulCageItemRenderer::getInstance)));
    public static final RegistryObject<Item> SOUL_SCROLL = ITEMS.register("soul_scroll", SoulScroll::new);
    public static final RegistryObject<Item> BOSS_SOUL_SCROLL = ITEMS.register("boss_soul_scroll",
        () -> new SoulScroll(() -> EntityType.WITHER, net.minecraft.item.Rarity.EPIC));
    public static final RegistryObject<ForgottenSoulScroll> FORGOTTEN_SOUL_SCROLL = ITEMS.register("forgotten_soul_scroll", ForgottenSoulScroll::new);
    // Keep the prototype courier scroll ID readable in existing worlds, outside the creative tab.
    public static final RegistryObject<Item> COURIER_SOUL_SCROLL = ITEMS.register("courier_soul_scroll",
        () -> new SoulScroll(SoulCatcherPlus.SOUL_COURIER, new Item.Properties().rarity(net.minecraft.item.Rarity.RARE)));
    /** Display-only item for the tab icon; omitted from the tab's obtainable equipment. */
    public static final RegistryObject<Item> SOUL_ICON = ITEMS.register("soul_icon", () -> new Item(new Item.Properties()));
    /** Compatibility alias for the original iron scythe. */
    public static final RegistryObject<Item> SCYTHE = IRON_SCYTHE;
    public static final RegistryObject<EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, EntityClassification.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ID + ":soul_remnant"));
    public static final RegistryObject<EntityType<SoulRavenEntity>> SOUL_RAVEN = ENTITIES.register("soul_raven",
        () -> EntityType.Builder.<SoulRavenEntity>of(SoulRavenEntity::new, EntityClassification.MISC)
            .sized(0.45f, 0.65f).clientTrackingRange(8).updateInterval(3)
            .build(ID + ":soul_raven"));
    public static final RegistryObject<EntityType<SoulCourierEntity>> SOUL_COURIER = ENTITIES.register("soul_courier",
        () -> EntityType.Builder.<SoulCourierEntity>of(SoulCourierEntity::new, EntityClassification.MISC)
            .sized(0.875f, 1.4875f).clientTrackingRange(8).updateInterval(3)
            .build(ID + ":soul_courier"));
    public static final RegistryObject<EntityType<SoulShamanEntity>> SOUL_SHAMAN = ENTITIES.register("soul_shaman",
        () -> EntityType.Builder.<SoulShamanEntity>of(SoulShamanEntity::new, EntityClassification.MISC)
            .sized(0.62f, 1.22f).clientTrackingRange(8).updateInterval(3)
            .build(ID + ":soul_shaman"));

    public SoulCatcherPlus() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.CLIENT, SoulClientConfig.SPEC);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        ENTITIES.register(bus);
        ForgottenSoulLoot.SERIALIZERS.register(bus);
        bus.addListener(SoulCatcherPlus::registerEntityAttributes);
        bus.addListener(SoulCatcherPlus::commonSetup);
        SoulEnchantments.ENCHANTMENTS.register(bus);
        SoulNetwork.register();
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(SOUL_RAVEN.get(), SoulRavenEntity.createAttributes());
        event.put(SOUL_COURIER.get(), SoulCourierEntity.createAttributes());
        event.put(SOUL_SHAMAN.get(), SoulShamanEntity.createAttributes());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SoulAdapters.register(SOUL_RAVEN.getId(), new SoulRavenAdapter());
            SoulAdapters.register(SOUL_COURIER.getId(), new SoulCourierAdapter());
            SoulAdapters.register(SOUL_SHAMAN.getId(), new SoulShamanAdapter());
            SoulAdapters.register(EntityType.WITHER.getRegistryName(), new dev.soulcatcherplus.companion.WitherSoulAdapter());
            SoulAdapters.register(EntityType.ENDER_DRAGON.getRegistryName(), new dev.soulcatcherplus.companion.DragonSoulAdapter());
        });
    }
}
