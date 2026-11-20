// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class ClientRegistration {
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(ClientControls.COLLECTION);
        event.register(ClientControls.COMMAND_WHEEL);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_BOLT.get(), SoulBoltRenderer::new);
        event.registerBlockEntityRenderer(dev.soulcatcherplus.altar.SoulAltars.TILE.get(), SoulDeviceRenderer::new);
        event.registerBlockEntityRenderer(dev.soulcatcherplus.furnace.SoulFurnaces.TILE.get(), SoulDeviceRenderer::new);
        event.registerBlockEntityRenderer(dev.soulcatcherplus.pylon.SoulPylons.TILE.get(), SoulDeviceRenderer::new);
        event.registerBlockEntityRenderer(dev.soulcatcherplus.projector.SoulProjectors.TILE.get(), SoulProjectorRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.REMNANT.get(), SoulRemnantRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_RAVEN.get(), SoulRavenRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_COURIER.get(), SoulCourierRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_SHAMAN.get(), SoulShamanRenderer::new);
    }
    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(dev.soulcatcherplus.altar.SoulAltars.MENU.get(), SoulAltarScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(dev.soulcatcherplus.furnace.SoulFurnaces.MENU.get(), SoulFurnaceScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(dev.soulcatcherplus.pylon.SoulPylons.MENU.get(), SoulPylonScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(dev.soulcatcherplus.projector.SoulProjectors.MENU.get(), SoulProjectorScreen::new);
        });
    }
}
