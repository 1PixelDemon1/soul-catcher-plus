// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulcatcherplus.entity.SoulRemnant;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

public final class SoulRemnantRenderer extends EntityRenderer<SoulRemnant> {
    private final ItemRenderer items;
    public SoulRemnantRenderer(EntityRendererProvider.Context context) { super(context); items = context.getItemRenderer(); }
    @Override public ResourceLocation getTextureLocation(SoulRemnant entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(SoulRemnant entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.3 + Math.sin((entity.tickCount + partial) * 0.08) * 0.12, 0);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partial) * 3));
        items.renderStatic(new ItemStack(Items.ECHO_SHARD), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, yaw, partial, pose, buffers, light);
    }
}
