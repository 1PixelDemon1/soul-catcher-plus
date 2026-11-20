// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.pylon.SoulPylonBlock;
import dev.soulcatcherplus.pylon.SoulPylonTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

/** Dynamic contents reuse the installed cage mesh; the spirit link is a dashed light ribbon. */
public final class SoulPylonRenderer extends TileEntityRenderer<SoulPylonTile> {
    private static final double LINK_HALF_WIDTH = .055;
    public static final ResourceLocation SCROLL = new ResourceLocation(SoulCatcherPlus.ID, "block/soul_pylon_scroll");
    public static final ResourceLocation BOSS_SCROLL = new ResourceLocation(SoulCatcherPlus.ID, "block/soul_pylon_boss_scroll");

    public SoulPylonRenderer(TileEntityRendererDispatcher dispatcher) { super(dispatcher); }

    @Override public void render(SoulPylonTile pylon, float partialTick, MatrixStack pose,
            IRenderTypeBuffer buffers, int light, int overlay) {
        if (pylon.getLevel() == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!pylon.scroll().isEmpty()) {
            ResourceLocation scrollModel = pylon.scroll().getItem() == SoulCatcherPlus.BOSS_SOUL_SCROLL.get() ? BOSS_SCROLL : SCROLL;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(-pylon.getBlockState().getValue(SoulPylonBlock.FACING).toYRot() - 180));
            pose.translate(-.5, 0, -.5);
            minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
                pylon.getBlockState(), minecraft.getModelManager().getModel(scrollModel), 1, 1, 1, light, overlay);
            pose.popPose();
        }
        long ticks = pylon.getLevel().getGameTime();
        if (!pylon.cage().isEmpty()) {
            pose.pushPose();
            float rotation = ((ticks % 240) + partialTick) * 1.5f;
            pose.translate(.5, 17.1 / 16 + Math.sin(((ticks % 80) + partialTick) * Math.PI / 40) * .01, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(rotation));
            pose.scale(.55f, .55f, .55f);
            SoulCageItemRenderer.getInstance().renderFloating(pylon.cage(), ((ticks % 80) + partialTick) / 20,
                pose, buffers, light, overlay);
            pose.popPose();
        }
        Entity guardian = pylon.getLevel().getEntity(pylon.guardianId());
        if (guardian != null && guardian.isAlive()) renderLink(pylon, guardian, partialTick, pose, buffers);
    }

    private static void renderLink(SoulPylonTile pylon, Entity guardian, float partialTick,
            MatrixStack pose, IRenderTypeBuffer buffers) {
        Vector3d start = new Vector3d(.5, 1.15, .5);
        Vector3d end = new Vector3d(MathHelper.lerp(partialTick, guardian.xo, guardian.getX()) - pylon.getBlockPos().getX(),
            MathHelper.lerp(partialTick, guardian.yo, guardian.getY()) - pylon.getBlockPos().getY() + guardian.getBbHeight() * .55,
            MathHelper.lerp(partialTick, guardian.zo, guardian.getZ()) - pylon.getBlockPos().getZ());
        if (end.distanceToSqr(start) > 32 * 32) return;
        int segments = Math.min(96, Math.max(8, (int)(end.distanceTo(start) * 5)));
        double phase = ((pylon.getLevel().getGameTime() % 80) + partialTick) / 80.0;
        IVertexBuilder buffer = buffers.getBuffer(RenderType.lightning());
        for (int index = 0; index < segments; index += 2) {
            double t = (index + phase * 2) / segments;
            if (t >= 1) continue;
            Vector3d a = linkPoint(start, end, t, phase);
            Vector3d b = linkPoint(start, end, Math.min(1, t + 1.2 / segments), phase);
            Vector3d tangent = b.subtract(a).normalize();
            Vector3d right = tangent.cross(new Vector3d(0, 1, 0));
            if (right.lengthSqr() < .001) right = new Vector3d(1, 0, 0);
            right = right.normalize().scale(LINK_HALF_WIDTH);
            Vector3d up = tangent.cross(right).normalize().scale(LINK_HALF_WIDTH);
            ribbon(pose, buffer, a, b, right);
            ribbon(pose, buffer, a, b, up);
        }
    }

    private static Vector3d linkPoint(Vector3d start, Vector3d end, double t, double phase) {
        return start.add(end.subtract(start).scale(t)).add(0,
            Math.sin(t * Math.PI) * (.25 + .06 * Math.sin((t * 3 - phase) * Math.PI * 2)), 0);
    }
    private static void ribbon(MatrixStack pose, IVertexBuilder buffer, Vector3d a, Vector3d b, Vector3d width) {
        vertex(pose, buffer, a.subtract(width)); vertex(pose, buffer, a.add(width));
        vertex(pose, buffer, b.add(width)); vertex(pose, buffer, b.subtract(width));
    }
    private static void vertex(MatrixStack pose, IVertexBuilder buffer, Vector3d point) {
        buffer.vertex(pose.last().pose(), (float)point.x, (float)point.y, (float)point.z).color(90, 235, 250, 220).endVertex();
    }
}
