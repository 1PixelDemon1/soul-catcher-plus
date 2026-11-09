// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulCourierModel;
import dev.soulcatcherplus.client.model.SoulCourierModel.Material;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class SoulCourierRenderer extends MobRenderer<SoulCourierEntity, SoulCourierModel> {
    private static final Material[] LAYERS = {Material.CHEST, Material.CLOAK, Material.STRAP, Material.SOUL};

    public SoulCourierRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context manager) {
        super(manager, new SoulCourierModel(), 0.35f);
        addLayer(new RenderLayer<SoulCourierEntity, SoulCourierModel>(this) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light,
                    SoulCourierEntity courier, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (courier.isInvisible()) return;
                for (Material material : LAYERS) {
                    getParentModel().render(material, pose, buffers.getBuffer(RenderType.entityTranslucent(material.texture)),
                        material == Material.SOUL ? LightTexture.pack(15, 15) : light,
                        getOverlayCoords(courier, 0), 1, 1, 1, 1);
                }
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulCourierEntity courier) { return Material.SKELETON.texture; }
}
