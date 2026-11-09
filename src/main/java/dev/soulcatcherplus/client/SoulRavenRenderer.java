// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.client.model.SoulRavenModel;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;

public final class SoulRavenRenderer extends MobRenderer<SoulRavenEntity, SoulRavenModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_raven.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_raven_eyes.png"));

    public SoulRavenRenderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context manager) {
        super(manager, new SoulRavenModel(), 0.2f);
        addLayer(new EyesLayer<SoulRavenEntity, SoulRavenModel>(this) {
            @Override public RenderType renderType() { return EYES; }
        });
    }

    @Override public ResourceLocation getTextureLocation(SoulRavenEntity raven) { return TEXTURE; }

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
