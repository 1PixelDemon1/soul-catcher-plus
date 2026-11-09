// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.client.model.SoulShamanModel;
import dev.soulcatcherplus.client.model.SoulShamanModel.Material;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;

public final class SoulShamanRenderer extends MobRenderer<SoulShamanEntity, SoulShamanModel> {
    private static final Material[] MATERIALS = Material.values();

    public SoulShamanRenderer(EntityRendererManager manager) {
        super(manager, new SoulShamanModel(), 0.3f);
        addLayer(new LayerRenderer<SoulShamanEntity, SoulShamanModel>(this) {
            @Override public void render(MatrixStack pose, IRenderTypeBuffer buffers, int light,
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
