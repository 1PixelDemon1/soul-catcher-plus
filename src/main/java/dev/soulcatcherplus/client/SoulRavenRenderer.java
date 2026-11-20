// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.client.model.SoulRavenModel;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.entity.player.PlayerEntity;

public final class SoulRavenRenderer extends MobRenderer<SoulRavenEntity, SoulRavenModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_raven.png");
    private static final ResourceLocation TIT_TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_long_tailed_tit.png");
    private final SoulRavenModel tit = new SoulRavenModel(true);

    public SoulRavenRenderer(EntityRendererManager manager) {
        super(manager, new SoulRavenModel(false), 0.2f);
        addLayer(new LayerRenderer<SoulRavenEntity, SoulRavenModel>(this) {
            @Override public void render(MatrixStack pose, IRenderTypeBuffer buffers, int light,
                    SoulRavenEntity raven, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (raven.isInvisible()) return;
                getParentModel().render(SoulRavenModel.Material.EMISSIVE, pose,
                    buffers.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(raven))), LightTexture.pack(15,15),
                    getOverlayCoords(raven,0), 1,1,1,1);
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulRavenEntity raven) {
        return raven.isLongTailedTit() ? TIT_TEXTURE : TEXTURE;
    }

    @Override public void render(SoulRavenEntity raven, float yaw, float partialTick,
            MatrixStack pose, IRenderTypeBuffer buffers, int light) {
        SoulRavenModel original = model;
        if (raven.isLongTailedTit()) model = tit;
        try { super.render(raven, yaw, partialTick, pose, buffers, light); }
        finally { model = original; }
    }

    @Override public Vector3d getRenderOffset(SoulRavenEntity raven, float partialTick) {
        if (SoulDissolves.renderingEcho()) return super.getRenderOffset(raven, partialTick);
        PlayerEntity owner = raven.perchOwner();
        if (!raven.isPerched() || owner == null) return super.getRenderOffset(raven, partialTick);
        // Attach to the interpolated player, avoiding the normal network-following lag.
        Vector3d position = new Vector3d(MathHelper.lerp(partialTick, raven.xo, raven.getX()),
            MathHelper.lerp(partialTick, raven.yo, raven.getY()), MathHelper.lerp(partialTick, raven.zo, raven.getZ()));
        return SoulRavenEntity.shoulderPosition(owner, partialTick).subtract(position);
    }

    @Override public boolean shouldRender(SoulRavenEntity raven, ClippingHelper clipping, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (raven.isPerched() && raven.perchOwner() == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
            return false;
        return super.shouldRender(raven, clipping, x, y, z);
    }
}
