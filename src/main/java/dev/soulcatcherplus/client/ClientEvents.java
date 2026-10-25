// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean renderingSoul;
    @SubscribeEvent public static <T extends LivingEntity, M extends EntityModel<T>> void render(RenderLivingEvent.Pre<T, M> event) {
        if (renderingSoul || !CompanionBrain.isSoul(event.getEntity())) return;
        event.setCanceled(true);
        T mob = (T) event.getEntity();
        ResourceLocation texture = event.getRenderer().getTextureLocation(mob);
        ResourceLocation type = mob.getType().builtInRegistryHolder().key().location();
        MultiBufferSource buffers = renderType -> SoulVisuals.tint(event.getMultiBufferSource()
            .getBuffer(SoulVisuals.remap(type, texture, renderType)));
        renderingSoul = true;
        try {
            event.getRenderer().render(mob, net.minecraft.util.Mth.rotLerp(event.getPartialTick(), mob.yRotO, mob.getYRot()),
                event.getPartialTick(), event.getPoseStack(), buffers, event.getPackedLight());
        } finally { renderingSoul = false; }
    }
}
