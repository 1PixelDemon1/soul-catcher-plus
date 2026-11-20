// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.altar.SoulAltarBlock;
import dev.soulcatcherplus.altar.SoulAltarTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.vector.Vector3f;

public final class SoulAltarRenderer extends TileEntityRenderer<SoulAltarTile> {
    public SoulAltarRenderer(TileEntityRendererDispatcher dispatcher) { super(dispatcher); }

    @Override public void render(SoulAltarTile altar, float partialTick, MatrixStack pose,
            IRenderTypeBuffer buffers, int light, int overlay) {
        if (altar.getLevel() == null || (altar.cageVisual().isEmpty() && altar.trayVisual().isEmpty())) return;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Vector3f.YP.rotationDegrees(-altar.getBlockState().getValue(SoulAltarBlock.FACING).toYRot() - 180));
        if (!altar.cageVisual().isEmpty()) {
            pose.pushPose();
            pose.translate(-4 / 16d, 13.01 / 16, 4 / 16d);
            pose.scale(.60f, .60f, .60f);
            float time = (altar.getLevel().getGameTime() % 80 + partialTick) / 20;
            SoulCageItemRenderer.getInstance().renderFloating(altar.cageVisual(), time, pose, buffers, light, overlay);
            pose.popPose();
        }
        if (!altar.trayVisual().isEmpty()) {
            pose.pushPose();
            pose.translate(1.5 / 16, 12.23 / 16, -4.25 / 16);
            pose.mulPose(Vector3f.XP.rotationDegrees(-90));
            pose.scale(.30f, .30f, .30f);
            Minecraft.getInstance().getItemRenderer().renderStatic(altar.trayVisual(), ItemCameraTransforms.TransformType.FIXED,
                light, overlay, pose, buffers);
            pose.popPose();
        }
        pose.popPose();
    }
}
