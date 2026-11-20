// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulcatcherplus.data.SoulAppearance;
import dev.soulcatcherplus.projector.SoulProjectorTile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

final class SoulProjectorRenderer implements BlockEntityRenderer<SoulProjectorTile, SoulProjectorRenderer.State> {
    private final BlockEntityRendererProvider.Context context;

    SoulProjectorRenderer(BlockEntityRendererProvider.Context context) { this.context = context; }

    static final class State extends BlockEntityRenderState {
        EntityRenderState mob;
        SoulAppearance appearance;
        float size, yaw;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(SoulProjectorTile tile, State state, float partial, Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(tile, state, breaking);
        state.mob = null;
        var mob = tile.displayEntity();
        if (mob == null) return;
        try {
            var settings = tile.settings();
            long time = tile.getLevel().getGameTime();
            mob.tickCount = (int)(time % 20000);
            var renderer = context.entityRenderer().getRenderer(mob);
            state.mob = renderer.createRenderState(mob, partial);
            state.mob.shadowPieces.clear();
            state.mob.outlineColor = 0;
            state.mob.nameTag = null;
            state.mob.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            state.appearance = settings.appearance();
            state.size = Math.min(1.5f / Math.max(.25f, mob.getBbHeight()),
                1.4f / Math.max(.25f, mob.getBbWidth())) * settings.scale() / 100f;
            state.yaw = settings.angle() + (settings.rotating() ? (time % 240 + partial) * 1.5f : 0);
        } catch (RuntimeException error) {
            tile.markDisplayFailed();
            state.mob = null;
            org.apache.logging.log4j.LogManager.getLogger().warn("Unable to extract soul projector model", error);
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        if (state.mob == null) return;
        var tint = state.appearance;
        var appearance = new SoulVisuals.Appearance(state.mob.entityType.builtInRegistryHolder().key().identifier(),
            ARGB.colorFromFloat(tint.opacity(), tint.red(), tint.green(), tint.blue()), null);
        pose.pushPose();
        pose.translate(.5, .65, .5);
        pose.rotate(Axis.YP.rotationDegrees(state.yaw));
        pose.scale(state.size, state.size, state.size);
        try {
            context.entityRenderer().getRenderer(state.mob).submit(state.mob, pose, new SoulSubmitNodes(nodes, appearance), camera);
        } finally {
            pose.popPose();
        }
    }
}
