// SPDX-License-Identifier: MIT
package dev.soulcatcherplus;

import dev.soulcatcherplus.entity.*;
import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.item.SoulScroll;
import dev.soulcatcherplus.item.ForgottenSoulScroll;
import dev.soulcatcherplus.loot.ForgottenSoulLoot;
import dev.soulcatcherplus.config.SoulClientConfig;
import dev.soulcatcherplus.enchantment.SoulEnchantments;
import dev.soulcatcherplus.item.SoulScythe;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.item.ScytheTier;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    // Preserve the original registry ID so existing iron scythes remain usable.
    public static final RegistryObject<Item> IRON_SCYTHE = ITEMS.register("soul_scythe", () -> new SoulScythe(ScytheTier.IRON, new Item.Properties().setId(ITEMS.key("soul_scythe"))));
    public static final RegistryObject<Item> DIAMOND_SCYTHE = ITEMS.register("diamond_soul_scythe", () -> new SoulScythe(ScytheTier.DIAMOND, new Item.Properties().setId(ITEMS.key("diamond_soul_scythe"))));
    public static final RegistryObject<Item> NETHERITE_SCYTHE = ITEMS.register("netherite_soul_scythe", () -> new SoulScythe(ScytheTier.NETHERITE, new Item.Properties().setId(ITEMS.key("netherite_soul_scythe"))));
    public static final RegistryObject<Item> SUPREME_SCYTHE = ITEMS.register("supreme_soul_scythe", () -> new SoulScythe(ScytheTier.SUPREME, new Item.Properties().setId(ITEMS.key("supreme_soul_scythe"))));
    public static final RegistryObject<Item> IRON_CAGE = ITEMS.register("iron_soul_cage", () -> new SoulCage(150, new Item.Properties().setId(ITEMS.key("iron_soul_cage"))));
    public static final RegistryObject<Item> DIAMOND_CAGE = ITEMS.register("diamond_soul_cage", () -> new SoulCage(400, new Item.Properties().setId(ITEMS.key("diamond_soul_cage"))));
    public static final RegistryObject<Item> NETHERITE_CAGE = ITEMS.register("netherite_soul_cage", () -> new SoulCage(1000, new Item.Properties().setId(ITEMS.key("netherite_soul_cage")).fireResistant()));
    public static final RegistryObject<Item> SUPREME_CAGE = ITEMS.register("supreme_soul_cage", () -> new SoulCage(2000, new Item.Properties().setId(ITEMS.key("supreme_soul_cage"))));
    public static final RegistryObject<Item> SOUL_SCROLL = ITEMS.register("soul_scroll", SoulScroll::new);
    public static final RegistryObject<Item> BOSS_SOUL_SCROLL = ITEMS.register("boss_soul_scroll", () -> new SoulScroll(() -> net.minecraft.world.entity.EntityTypes.WITHER, new Item.Properties().setId(ITEMS.key("boss_soul_scroll")).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<ForgottenSoulScroll> FORGOTTEN_SOUL_SCROLL = ITEMS.register("forgotten_soul_scroll", ForgottenSoulScroll::new);
    public static final RegistryObject<Item> COURIER_SOUL_SCROLL = ITEMS.register("courier_soul_scroll", () -> new SoulScroll(SoulCatcherPlus.SOUL_COURIER, new Item.Properties().setId(ITEMS.key("courier_soul_scroll")).rarity(net.minecraft.world.item.Rarity.RARE)));
    /** Display-only item for the tab icon; omitted from the tab's obtainable equipment. */
    public static final RegistryObject<Item> SOUL_ICON = ITEMS.register("soul_icon", () -> new Item(new Item.Properties().setId(ITEMS.key("soul_icon"))));
    /** Compatibility alias for the original iron scythe. */
    public static final RegistryObject<Item> SCYTHE = IRON_SCYTHE;
    public static final RegistryObject<CreativeModeTab> SOUL_TAB = CREATIVE_TABS.register("soulcatcherplus", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.soulcatcherplus"))
            .icon(() -> new ItemStack(SOUL_ICON.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> {
                output.accept(IRON_SCYTHE.get()); output.accept(DIAMOND_SCYTHE.get()); output.accept(NETHERITE_SCYTHE.get()); output.accept(SUPREME_SCYTHE.get());
                output.accept(SOUL_SCROLL.get()); output.accept(BOSS_SOUL_SCROLL.get());
                for (ItemStack scroll : FORGOTTEN_SOUL_SCROLL.get().creativeVariants()) output.accept(scroll);
                output.accept(IRON_CAGE.get()); output.accept(DIAMOND_CAGE.get()); output.accept(NETHERITE_CAGE.get()); output.accept(SUPREME_CAGE.get());
            }).build());
    public static final RegistryObject<EntityType<SoulRemnant>> REMNANT = ENTITIES.register("soul_remnant",
        () -> EntityType.Builder.<SoulRemnant>of(SoulRemnant::new, MobCategory.MISC)
            .sized(0.6f, 0.8f).clientTrackingRange(8).updateInterval(10)
            .build(ENTITIES.key("soul_remnant")));

    public static final RegistryObject<EntityType<SoulRavenEntity>> SOUL_RAVEN = ENTITIES.register("soul_raven", () -> EntityType.Builder.<SoulRavenEntity>of(SoulRavenEntity::new, MobCategory.MISC).sized(.45f,.65f).clientTrackingRange(8).updateInterval(3).build(ENTITIES.key("soul_raven")));
    public static final RegistryObject<EntityType<SoulCourierEntity>> SOUL_COURIER = ENTITIES.register("soul_courier", () -> EntityType.Builder.<SoulCourierEntity>of(SoulCourierEntity::new, MobCategory.MISC).sized(.875f,1.4875f).clientTrackingRange(8).updateInterval(3).build(ENTITIES.key("soul_courier")));
    public static final RegistryObject<EntityType<SoulShamanEntity>> SOUL_SHAMAN = ENTITIES.register("soul_shaman", () -> EntityType.Builder.<SoulShamanEntity>of(SoulShamanEntity::new, MobCategory.MISC).sized(.62f,1.22f).clientTrackingRange(8).updateInterval(3).build(ENTITIES.key("soul_shaman")));
    public SoulCatcherPlus(FMLJavaModLoadingContext context) {
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, SoulClientConfig.SPEC);
        var bus = context.getModBusGroup();
        net.minecraftforge.event.entity.EntityAttributeCreationEvent.BUS.addListener((net.minecraftforge.event.entity.EntityAttributeCreationEvent event) -> {
            event.put(SOUL_RAVEN.get(), SoulRavenEntity.createAttributes());
            event.put(SOUL_COURIER.get(), SoulCourierEntity.createAttributes());
            event.put(SOUL_SHAMAN.get(), SoulShamanEntity.createAttributes());
        });
        net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent.getBus(bus).addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            SoulAdapters.register(SOUL_RAVEN.getId(), new SoulRavenAdapter());
            SoulAdapters.register(SOUL_COURIER.getId(), new SoulCourierAdapter());
            SoulAdapters.register(SOUL_SHAMAN.getId(), new SoulShamanAdapter());
        }));
        ForgottenSoulLoot.SERIALIZERS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        CREATIVE_TABS.register(bus);
        SoulNetwork.register();
    }
}
