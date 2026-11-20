// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.projector.SoulProjectorTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Mob;
import com.mojang.math.Vector3f;

/** Renders one cached visual model; no world insertion, AI, interaction or soul reservation. */
public final class SoulProjectorRenderer implements BlockEntityRenderer<SoulProjectorTile> {
    public SoulProjectorRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SoulProjectorTile tile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Mob mob = tile.displayEntity();
        if (mob == null) return;
        var settings = tile.settings();
        SoulAppearances.project(mob, settings.appearance());
        long time = tile.getLevel().getGameTime();
        mob.tickCount = (int)(time % 20000);
        mob.yRotO = mob.yBodyRot = mob.yBodyRotO = mob.yHeadRot = mob.yHeadRotO = 0;
        mob.xRotO = 0;
        mob.setYRot(0);
        mob.setXRot(0);
        float fit = Math.min(1.5f / Math.max(.25f, mob.getBbHeight()), 1.4f / Math.max(.25f, mob.getBbWidth()));
        float size = fit * settings.scale() / 100f;
        float angle = settings.angle() + (settings.rotating() ? ((time % 240) + partialTick) * 1.5f : 0);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // A third-party mob renderer may throw with unbalanced pushes; isolate its matrix stack.
        PoseStack projectionPose = new PoseStack();
        projectionPose.last().pose().multiply(pose.last().pose());
        projectionPose.last().normal().load(pose.last().normal());
        try {
            projectionPose.translate(.5, .65, .5);
            projectionPose.mulPose(Vector3f.YP.rotationDegrees(angle));
            projectionPose.scale(size, size, size);
            dispatcher.setRenderShadow(false);
            dispatcher.render(mob, 0, 0, 0, 0, partialTick, projectionPose, buffers, 15728880);
        } catch (RuntimeException error) {
            tile.markDisplayFailed();
            org.apache.logging.log4j.LogManager.getLogger().warn("Unable to render soul projector model", error);
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }
}
