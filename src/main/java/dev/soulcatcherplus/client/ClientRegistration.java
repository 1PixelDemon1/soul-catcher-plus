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
        event.registerEntityRenderer(SoulCatcherPlus.REMNANT.get(), SoulRemnantRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_RAVEN.get(), SoulRavenRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_COURIER.get(), SoulCourierRenderer::new);
        event.registerEntityRenderer(SoulCatcherPlus.SOUL_SHAMAN.get(), SoulShamanRenderer::new);
    }
}
