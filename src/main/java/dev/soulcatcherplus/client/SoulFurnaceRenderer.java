// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.furnace.SoulFurnaceTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.mojang.math.Axis;

public final class SoulFurnaceRenderer implements BlockEntityRenderer<SoulFurnaceTile> {
    public SoulFurnaceRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SoulFurnaceTile furnace, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (furnace.getLevel() == null || furnace.cageVisual().isEmpty()) return;
        var state = furnace.getBlockState();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() - 180));
        // Sit on the two-texel sill, centered in the five-texel-deep front recess.
        pose.translate(0, 2 / 16.0, -5.5 / 16.0);
        pose.scale(.36f, .36f, .36f);
        float time = (furnace.getLevel().getGameTime() % 80 + partialTick) / 20;
        SoulCageItemRenderer.getInstance().renderFloating(furnace.cageVisual(), time, pose, buffers, light, overlay);
        pose.popPose();
    }
}
