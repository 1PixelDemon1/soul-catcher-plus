// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.altar.SoulAltarTile;
import dev.soulcatcherplus.client.model.SoulCageModel;
import dev.soulcatcherplus.furnace.SoulFurnaceTile;
import dev.soulcatcherplus.pylon.SoulPylonTile;
import java.util.EnumMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Extract the visible device contents before the frame is queued. */
final class SoulDeviceRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, SoulDeviceRenderer.State> {
    private final BlockEntityRendererProvider.Context context;
    private final EnumMap<SoulCageModel.Material, SoulCageItemRenderer> cages = new EnumMap<>(SoulCageModel.Material.class);

    SoulDeviceRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
        for (var material : SoulCageModel.Material.values()) cages.put(material, new SoulCageItemRenderer(material));
    }

    static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        SoulCageModel.Material cage;
        float yaw, time;
        int kind;
        Vec3 link;
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T tile, State state, float partial, Vec3 camera,
            ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderState.extractBase(tile, state, breaking);
        state.item.clear();
        state.cage = null;
        state.link = null;
        if (tile.getLevel() == null) return;
        state.yaw = tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
        state.time = (tile.getLevel().getGameTime() % 240 + partial) / 20f;
        if (tile instanceof SoulPylonTile pylon) {
            state.kind = 0;
            state.cage = material(pylon.cage());
            resolve(pylon.scroll(), tile, state);
            Entity entity = tile.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel client
                ? client.getEntity(pylon.guardianId()) : null;
            if (entity != null && entity.isAlive()) {
                Vec3 target = new Vec3(Mth.lerp(partial, entity.xo, entity.getX()),
                    Mth.lerp(partial, entity.yo, entity.getY()) + entity.getBbHeight() * .55,
                    Mth.lerp(partial, entity.zo, entity.getZ())).subtract(Vec3.atLowerCornerOf(tile.getBlockPos()));
                if (target.distanceToSqr(new Vec3(.5, 1.15, .5)) < 32 * 32) state.link = target;
            }
        } else if (tile instanceof SoulAltarTile altar) {
            state.kind = 1;
            state.cage = material(altar.cageVisual());
            resolve(altar.trayVisual(), tile, state);
        } else if (tile instanceof SoulFurnaceTile furnace) {
            state.kind = 2;
            state.cage = material(furnace.cageVisual());
        }
    }

    private void resolve(ItemStack item, BlockEntity tile, State state) {
        context.itemModelResolver().updateForTopItem(state.item, item, ItemDisplayContext.FIXED, tile.getLevel(), null, 0);
    }

    private static SoulCageModel.Material material(ItemStack item) {
        if (item.is(SoulCatcherPlus.IRON_CAGE.get())) return SoulCageModel.Material.IRON;
        if (item.is(SoulCatcherPlus.DIAMOND_CAGE.get())) return SoulCageModel.Material.DIAMOND;
        if (item.is(SoulCatcherPlus.NETHERITE_CAGE.get())) return SoulCageModel.Material.NETHERITE;
        return item.is(SoulCatcherPlus.SUPREME_CAGE.get()) ? SoulCageModel.Material.SUPREME : null;
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.rotate(Axis.YP.rotationDegrees(-state.yaw - 180));
        if (!state.item.isEmpty()) {
            pose.pushPose();
            if (state.kind == 0) pose.translate(0, .87, -.15);
            else pose.translate(1.5 / 16, 12.23 / 16, -4.25 / 16);
            pose.rotate(Axis.XP.rotationDegrees(-90));
            pose.scale(.30f, .30f, .30f);
            state.item.submit(pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.cage != null) {
            pose.pushPose();
            if (state.kind == 0) {
                pose.translate(0, 17.1 / 16 + Math.sin(state.time * Math.PI / 2) * .01, 0);
                pose.rotate(Axis.YP.rotationDegrees(state.time * 30));
                pose.scale(.55f, .55f, .55f);
            } else if (state.kind == 1) {
                pose.translate(-.25, 13.01 / 16, .25);
                pose.scale(.6f, .6f, .6f);
            } else {
                pose.translate(0, .19, -.44);
                pose.scale(.40f, .40f, .40f);
            }
            cages.get(state.cage).submitFloating(state.time, pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        if (state.link != null) link(state.link, state.time, pose, nodes);
    }

    private static void link(Vec3 target, float time, PoseStack pose, SubmitNodeCollector nodes) {
        Vec3 start = new Vec3(.5, 1.15, .5);
        Vec3 direction = target.subtract(start);
        int segments = Math.min(96, Math.max(8, (int)(direction.length() * 5)));
        Vec3 tangent = direction.normalize();
        Vec3 right = tangent.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < .001) right = new Vec3(1, 0, 0);
        Vec3 width = right.normalize().scale(.055);
        Vec3 depth = tangent.cross(width).normalize().scale(.055);
        nodes.submitCustomGeometry(pose, RenderTypes.lightning(), (frame, buffer) -> {
            for (int index = 0; index < segments; index += 2) {
                double along = (index + (time % 2)) / segments;
                if (along >= 1) continue;
                Vec3 a = start.add(direction.scale(along));
                Vec3 b = start.add(direction.scale(Math.min(1, along + 1.2 / segments)));
                for (Vec3 axis : new Vec3[] {width, depth}) {
                    for (Vec3 vertex : new Vec3[] {a.subtract(axis), a.add(axis), b.add(axis), b.subtract(axis)})
                        buffer.addVertex(frame.pose(), (float)vertex.x, (float)vertex.y, (float)vertex.z).setColor(90, 235, 250, 220);
                }
            }
        });
    }
}
