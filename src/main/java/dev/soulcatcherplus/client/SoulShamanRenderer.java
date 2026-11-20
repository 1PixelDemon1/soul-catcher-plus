// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.SoulCatcherPlus;
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
    private static final ResourceLocation MIKU_TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_shaman_miku.png");
    private final SoulShamanModel miku = new SoulShamanModel(true);

    public SoulShamanRenderer(EntityRendererManager manager) {
        super(manager, new SoulShamanModel(false), 0.3f);
        addLayer(new LayerRenderer<SoulShamanEntity, SoulShamanModel>(this) {
            @Override public void render(MatrixStack pose, IRenderTypeBuffer buffers, int light,
                    SoulShamanEntity shaman, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (shaman.isInvisible()) return;
                if (shaman.isMiku()) {
                    getParentModel().render(Material.SOUL_LIGHT, pose, buffers.getBuffer(RenderType.entityTranslucentCull(MIKU_TEXTURE)),
                        LightTexture.pack(15, 15), getOverlayCoords(shaman, 0), 1, 1, 1, 1);
                    return;
                }
                for (Material material : MATERIALS) {
                    if (material == Material.CLOTH) continue;
                    getParentModel().render(material, pose, buffers.getBuffer(RenderType.entityTranslucentCull(material.texture)),
                        material.emissive ? LightTexture.pack(15, 15) : light,
                        getOverlayCoords(shaman, 0), 1, 1, 1, 1);
                }
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulShamanEntity shaman) {
        return shaman.isMiku() ? MIKU_TEXTURE : Material.CLOTH.texture;
    }

    @Override public void render(SoulShamanEntity shaman, float yaw, float partialTick,
            MatrixStack pose, IRenderTypeBuffer buffers, int light) {
        SoulShamanModel original = model;
        if (shaman.isMiku()) model = miku;
        try { super.render(shaman, yaw, partialTick, pose, buffers, light); }
        finally { model = original; }
    }
}
