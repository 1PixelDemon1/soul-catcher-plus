// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.client.model.SoulCourierModel;
import dev.soulcatcherplus.client.model.SoulCourierModel.Material;
import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.data.SoulAppearance;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;

public final class SoulCourierRenderer extends MobRenderer<SoulCourierEntity, SoulCourierModel> {
    // Draw the courier first so the translucent chest can blend over its silhouette.
    private static final Material[] LAYERS = {Material.CLOAK, Material.STRAP, Material.SOUL, Material.CHEST};

    public SoulCourierRenderer(EntityRendererManager manager) {
        super(manager, new SoulCourierModel(), 0.35f);
        addLayer(new LayerRenderer<SoulCourierEntity, SoulCourierModel>(this) {
            @Override public void render(MatrixStack pose, IRenderTypeBuffer buffers, int light,
                    SoulCourierEntity courier, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (courier.isInvisible()) return;
                float opacity = CompanionBrain.isSoul(courier) ? SoulAppearances.of(courier).opacity() : 1.0f;
                // The soul buffer already multiplies every layer by opacity.
                float chestAlpha = Math.max(SoulAppearance.MIN_OPACITY, opacity * opacity) / opacity;
                for (Material material : LAYERS) {
                    float alpha = material == Material.CHEST ? chestAlpha : 1.0f;
                    getParentModel().render(material, pose, buffers.getBuffer(RenderType.entityTranslucentCull(material.texture)),
                        material == Material.SOUL ? LightTexture.pack(15, 15) : light,
                        getOverlayCoords(courier, 0), 1, 1, 1, alpha);
                }
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulCourierEntity courier) { return Material.SKELETON.texture; }
}
