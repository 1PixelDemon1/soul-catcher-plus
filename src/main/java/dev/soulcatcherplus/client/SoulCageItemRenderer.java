// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.client.model.SoulCageModel;
import dev.soulcatcherplus.client.model.SoulCageModel.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Hand-only renderer; the baked inventory sprite remains the default elsewhere. */
public final class SoulCageItemRenderer extends ItemStackTileEntityRenderer {
    // Centre of the upper chain bar in the approved Blockbench geometry.
    private static final double GRIP_HEIGHT = 15.225 / 16;
    private static final ResourceLocation MATERIAL = new ResourceLocation(SoulCatcherPlus.ID,
        "textures/item/cage_material.png");
    private static final RenderType FRAME = RenderType.entityCutoutNoCull(MATERIAL);
    private static final RenderType GLASS = RenderType.entityTranslucent(MATERIAL);
    private final SoulCageModel iron = new SoulCageModel(Material.IRON);
    private final SoulCageModel diamond = new SoulCageModel(Material.DIAMOND);
    private final SoulCageModel netherite = new SoulCageModel(Material.NETHERITE);
    private final SoulCageModel supreme = new SoulCageModel(Material.SUPREME);

    private SoulCageItemRenderer() {}

    public static SoulCageItemRenderer getInstance() { return Holder.INSTANCE; }

    private static final class Holder {
        private static final SoulCageItemRenderer INSTANCE = new SoulCageItemRenderer();
    }

    @Override public void renderByItem(ItemStack stack, TransformType transform, MatrixStack pose,
            IRenderTypeBuffer buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        SoulCageModel model;
        if (stack.getItem() == SoulCatcherPlus.IRON_CAGE.get()) model = iron;
        else if (stack.getItem() == SoulCatcherPlus.DIAMOND_CAGE.get()) model = diamond;
        else if (stack.getItem() == SoulCatcherPlus.NETHERITE_CAGE.get()) model = netherite;
        else if (stack.getItem() == SoulCatcherPlus.SUPREME_CAGE.get()) model = supreme;
        else return;
        float seconds = ((minecraft.level.getGameTime() % 80) + minecraft.getFrameTime()) / 20;
        pose.pushPose();
        // Undo ItemRenderer's corner offset, then place the chain grip at the hand origin.
        pose.translate(.5, .5 - GRIP_HEIGHT, .5);
        model.render(SoulCageModel.Surface.FRAME, seconds, pose,
            ItemRenderer.getFoilBufferDirect(buffers, FRAME, false, stack.hasFoil()), light, overlay);
        model.render(SoulCageModel.Surface.SOUL, seconds, pose,
            buffers.getBuffer(FRAME), 0xF000F0, overlay);
        model.render(SoulCageModel.Surface.GLASS, seconds, pose,
            buffers.getBuffer(GLASS), light, overlay);
        pose.popPose();
    }
}
