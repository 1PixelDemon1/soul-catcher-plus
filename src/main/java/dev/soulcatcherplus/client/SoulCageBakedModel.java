// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.model.BakedModelWrapper;

/** Select the animated mesh only in hands, without mutable shared render context. */
final class SoulCageBakedModel extends BakedModelWrapper<IBakedModel> {
    private final IBakedModel handModel;

    SoulCageBakedModel(IBakedModel sprite) {
        super(sprite);
        handModel = new BakedModelWrapper<IBakedModel>(sprite) {
            @Override public boolean isCustomRenderer() { return true; }
        };
    }

    @Override public boolean doesHandlePerspectives() { return true; }

    @Override public IBakedModel handlePerspective(TransformType transform, MatrixStack pose) {
        switch (transform) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                // Forge 1.16.5 copies this temporary stack only after a push; leave it pushed.
                pose.pushPose();
                // ForgeHooksClient mirrors this pose for the left hand itself.
                pose.translate(.28, .4, -.15);
                pose.mulPose(Vector3f.YP.rotationDegrees(30));
                pose.scale(.5f, .5f, .5f);
                return handModel;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                pose.pushPose();
                // HeldItemLayer rotates item space across the arm; keep the cage hanging below its grip.
                // Keep the handle at the lower edge of the fist, rather than inside the palm.
                pose.translate(0, -.125, 0);
                pose.mulPose(Vector3f.XP.rotationDegrees(90));
                pose.scale(.55f, .55f, .55f);
                return handModel;
            default:
                return originalModel.handlePerspective(transform, pose);
        }
    }
}
