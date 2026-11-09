// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
    @SubscribeEvent public static void bakeModels(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
        for (var item : java.util.List.of(SoulCatcherPlus.IRON_CAGE, SoulCatcherPlus.DIAMOND_CAGE, SoulCatcherPlus.NETHERITE_CAGE, SoulCatcherPlus.SUPREME_CAGE)) {
            var key = new net.minecraft.client.resources.model.ModelResourceLocation(item.getId(), "inventory");
            var sprite = event.getModels().get(key);
            if (sprite != null) event.getModels().put(key, new SoulCageBakedModel(sprite));
        }
    }
}
