// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.client.model.SoulRavenModel;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

public final class SoulRavenRenderer extends MobRenderer<SoulRavenEntity, SoulRavenModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_raven.png");
    private static final ResourceLocation TIT_TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_long_tailed_tit.png");
    private final SoulRavenModel tit = new SoulRavenModel(true);

    public SoulRavenRenderer(EntityRendererProvider.Context manager) {
        super(manager, new SoulRavenModel(false), 0.2f);
        addLayer(new RenderLayer<SoulRavenEntity, SoulRavenModel>(this) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light,
                    SoulRavenEntity raven, float swing, float amount, float partialTick,
                    float age, float yaw, float pitch) {
                if (raven.isInvisible()) return;
                getParentModel().render(SoulRavenModel.Material.EMISSIVE, pose,
                    buffers.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(raven))), LightTexture.FULL_BRIGHT,
                    getOverlayCoords(raven,0), 1,1,1,1);
            }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulRavenEntity raven) {
        return raven.isLongTailedTit() ? TIT_TEXTURE : TEXTURE;
    }

    @Override public void render(SoulRavenEntity raven, float yaw, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light) {
        SoulRavenModel original = model;
        if (raven.isLongTailedTit()) model = tit;
        try { super.render(raven, yaw, partialTick, pose, buffers, light); }
        finally { model = original; }
    }

    @Override public Vec3 getRenderOffset(SoulRavenEntity raven, float partialTick) {
        if (SoulDissolves.renderingEcho()) return super.getRenderOffset(raven, partialTick);
        Player owner = raven.perchOwner();
        if (!raven.isPerched() || owner == null) return super.getRenderOffset(raven, partialTick);
        // Attach to the interpolated player, avoiding the normal network-following lag.
        Vec3 position = new Vec3(Mth.lerp(partialTick, raven.xo, raven.getX()),
            Mth.lerp(partialTick, raven.yo, raven.getY()), Mth.lerp(partialTick, raven.zo, raven.getZ()));
        return SoulRavenEntity.shoulderPosition(owner, partialTick).subtract(position);
    }

    @Override public boolean shouldRender(SoulRavenEntity raven, Frustum clipping, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (raven.isPerched() && raven.perchOwner() == minecraft.player && minecraft.options.getCameraType().isFirstPerson())
            return false;
        return super.shouldRender(raven, clipping, x, y, z);
    }
}
