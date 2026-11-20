// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.altar.SoulAltars;
import dev.soulcatcherplus.furnace.SoulFurnaces;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRegistration {
    @SubscribeEvent public static void extraModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        net.minecraftforge.client.model.ModelLoader.addSpecialModel(SoulPylonRenderer.SCROLL);
        net.minecraftforge.client.model.ModelLoader.addSpecialModel(SoulPylonRenderer.BOSS_SCROLL);
    }
    @SubscribeEvent public static void bakeModels(ModelBakeEvent event) {
        wrapCageModel(event, SoulCatcherPlus.IRON_CAGE.getId());
        wrapCageModel(event, SoulCatcherPlus.DIAMOND_CAGE.getId());
        wrapCageModel(event, SoulCatcherPlus.NETHERITE_CAGE.getId());
        wrapCageModel(event, SoulCatcherPlus.SUPREME_CAGE.getId());
    }

    private static void wrapCageModel(ModelBakeEvent event, ResourceLocation itemId) {
        ModelResourceLocation cage = new ModelResourceLocation(itemId, "inventory");
        IBakedModel sprite = event.getModelRegistry().get(cage);
        if (sprite != null) event.getModelRegistry().put(cage, new SoulCageBakedModel(sprite));
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_BOLT.get(), SoulBoltRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.REMNANT.get(), SoulRemnantRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_RAVEN.get(), SoulRavenRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_COURIER.get(), SoulCourierRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_SHAMAN.get(), SoulShamanRenderer::new);
        event.enqueueWork(() -> {
            net.minecraft.client.gui.ScreenManager.register(SoulFurnaces.MENU.get(), SoulFurnaceScreen::new);
            ClientRegistry.bindTileEntityRenderer(SoulFurnaces.TILE.get(), SoulFurnaceRenderer::new);
            SoulBowModels.register();
            net.minecraft.client.gui.ScreenManager.register(SoulAltars.MENU.get(), SoulAltarScreen::new);
            ClientRegistry.bindTileEntityRenderer(SoulAltars.TILE.get(), SoulAltarRenderer::new);
            net.minecraft.client.gui.ScreenManager.register(dev.soulcatcherplus.pylon.SoulPylons.MENU.get(), SoulPylonScreen::new);
            net.minecraft.client.gui.ScreenManager.register(dev.soulcatcherplus.projector.SoulProjectors.MENU.get(), SoulProjectorScreen::new);
            ClientRegistry.bindTileEntityRenderer(dev.soulcatcherplus.projector.SoulProjectors.TILE.get(), SoulProjectorRenderer::new);
            ClientRegistry.bindTileEntityRenderer(dev.soulcatcherplus.pylon.SoulPylons.TILE.get(), SoulPylonRenderer::new);
            ClientRegistry.registerKeyBinding(ClientControls.COLLECTION);
            ClientRegistry.registerKeyBinding(ClientControls.COMMAND_WHEEL);
        });
    }
}
