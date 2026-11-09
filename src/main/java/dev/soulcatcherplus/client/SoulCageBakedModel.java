// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import com.mojang.math.Vector3f;
import net.minecraftforge.client.model.BakedModelWrapper;

/** Select the animated mesh only in hands, without mutable shared render context. */
final class SoulCageBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel handModel;

    SoulCageBakedModel(BakedModel sprite) {
        super(sprite);
        handModel = new BakedModelWrapper<BakedModel>(sprite) {
            @Override public boolean isCustomRenderer() { return true; }
        };
    }


    @Override public BakedModel applyTransform(TransformType transform, PoseStack pose, boolean leftHand) {
        switch (transform) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                pose.translate(leftHand ? -.28 : .28, .4, -.15);
                pose.mulPose(Vector3f.YP.rotationDegrees(leftHand ? -30 : 30));
                pose.scale(.5f, .5f, .5f);
                return handModel;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                // HeldItemLayer rotates item space across the arm; keep the cage hanging below its grip.
                // Keep the handle at the lower edge of the fist, rather than inside the palm.
                pose.translate(0, -.125, 0);
                pose.mulPose(Vector3f.XP.rotationDegrees(90));
                pose.scale(.55f, .55f, .55f);
                return handModel;
            default:
                return originalModel.applyTransform(transform, pose, leftHand);
        }
    }
}
