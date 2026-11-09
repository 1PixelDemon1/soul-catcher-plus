// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.soulcatcherplus.client.model.SoulCageModel;
import dev.soulcatcherplus.client.model.SoulCageModel.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.Locale;
import java.util.function.Consumer;

/** Selected by item definitions only in hand; inventory and ground retain their sprites. */
public final class SoulCageItemRenderer implements SpecialModelRenderer<Float> {
    private static final float GRIP_HEIGHT = 15.225f / 16;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("soulcatcherplus", "textures/item/cage_material.png");
    private final SoulCageModel model;

    private SoulCageItemRenderer(Material material) { model = new SoulCageModel(material); }

    @Override public Float extractArgument(ItemStack stack) {
        var client = Minecraft.getInstance();
        return client.level == null ? 0 : ((client.level.getGameTime() % 80)
            + client.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20;
    }

    @Override public void submit(Float time, PoseStack pose, SubmitNodeCollector nodes,
            int light, int overlay, boolean foil, int outline) {
        float seconds = time == null ? 0 : time;
        pose.pushPose();
        // Authored coordinates are centered on the chain grip, below the hand.
        pose.translate(0, -GRIP_HEIGHT, 0);
        nodes.submitCustomGeometry(pose, foil ? RenderTypes.armorCutoutNoCullGlint(TEXTURE)
                : RenderTypes.entityCutout(TEXTURE), (frame, buffer) ->
            model.render(SoulCageModel.Surface.FRAME, seconds, SoulModelRenderer.poseFrom(frame), buffer, light, overlay));
        nodes.submitCustomGeometry(pose, RenderTypes.entityCutout(TEXTURE), (frame, buffer) ->
            model.render(SoulCageModel.Surface.SOUL, seconds, SoulModelRenderer.poseFrom(frame), buffer, 0xF000F0, overlay));
        nodes.order(1).submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (frame, buffer) ->
            model.render(SoulCageModel.Surface.GLASS, seconds, SoulModelRenderer.poseFrom(frame), buffer, light, overlay));
        pose.popPose();
    }

    @Override public void getExtents(Consumer<Vector3fc> output) {
        for (int x : new int[] {-1, 1}) for (int y : new int[] {0, 1}) for (int z : new int[] {-1, 1})
            output.accept(new Vector3f(x * .28f, y - GRIP_HEIGHT, z * .28f));
    }

    public record Unbaked(Material material) implements SpecialModelRenderer.Unbaked<Float> {
        public static final MapCodec<Unbaked> MAP_CODEC = Codec.STRING
            .xmap(name -> Material.valueOf(name.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT))
            .fieldOf("material").xmap(Unbaked::new, Unbaked::material);
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
        @Override public SoulCageItemRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new SoulCageItemRenderer(material);
        }
    }
}
