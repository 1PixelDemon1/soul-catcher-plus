// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
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
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.REMNANT.get(), SoulRemnantRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_RAVEN.get(), SoulRavenRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_COURIER.get(), SoulCourierRenderer::new);
        RenderingRegistry.registerEntityRenderingHandler(SoulCatcherPlus.SOUL_SHAMAN.get(), SoulShamanRenderer::new);
        event.enqueueWork(() -> {
            ClientRegistry.registerKeyBinding(ClientControls.COLLECTION);
            ClientRegistry.registerKeyBinding(ClientControls.COMMAND_WHEEL);
        });
    }
}
