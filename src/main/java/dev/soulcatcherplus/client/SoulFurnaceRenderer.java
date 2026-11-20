// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.furnace.SoulFurnaceTile;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.vector.Vector3f;

public final class SoulFurnaceRenderer extends TileEntityRenderer<SoulFurnaceTile> {
    public SoulFurnaceRenderer(TileEntityRendererDispatcher dispatcher) { super(dispatcher); }
    @Override public void render(SoulFurnaceTile furnace, float partialTick, MatrixStack pose,
            IRenderTypeBuffer buffers, int light, int overlay) {
        if (furnace.getLevel() == null || furnace.cageVisual().isEmpty()) return;
        var state = furnace.getBlockState();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Vector3f.YP.rotationDegrees(-state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() - 180));
        // Sit on the two-texel sill, centered in the five-texel-deep front recess.
        pose.translate(0, 2 / 16.0, -5.5 / 16.0);
        pose.scale(.36f, .36f, .36f);
        float time = (furnace.getLevel().getGameTime() % 80 + partialTick) / 20;
        SoulCageItemRenderer.getInstance().renderFloating(furnace.cageVisual(), time, pose, buffers, light, overlay);
        pose.popPose();
    }
}
