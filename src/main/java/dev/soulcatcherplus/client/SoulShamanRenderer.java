// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.client.model.SoulShamanModel;
import dev.soulcatcherplus.client.model.SoulShamanModel.Material;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class SoulShamanRenderer extends MobRenderer<SoulShamanEntity, SoulShamanModel> {
    private static final Material[] MATERIALS = Material.values();

    public SoulShamanRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context manager) {
        super(manager, new SoulShamanModel(), 0.3f);
        addLayer(new RenderLayer<SoulShamanEntity, SoulShamanModel>(this) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light,
                    SoulShamanEntity shaman, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (shaman.isInvisible()) return;
                for (Material material : MATERIALS) {
                    if (material == Material.CLOTH) continue;
                    getParentModel().render(material, pose, buffers.getBuffer(RenderType.entityTranslucent(material.texture)),
                        material.emissive ? LightTexture.pack(15, 15) : light,
                        getOverlayCoords(shaman, 0), 1, 1, 1, 1);
                }
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulShamanEntity shaman) { return Material.CLOTH.texture; }
}
