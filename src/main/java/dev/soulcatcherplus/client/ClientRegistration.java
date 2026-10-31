// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRegistration {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.REMNANT.get(), SoulRemnantRenderer::new);
        event.enqueueWork(() -> {
            ClientRegistry.registerKeyBinding(ClientControls.COLLECTION);
            ClientRegistry.registerKeyBinding(ClientControls.COMMAND_WHEEL);
        });
    }
}
