// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import java.util.Map;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

/** Keeps the approved Blockbench hierarchy, face UVs and separate vanilla materials. */
public final class SoulCourierModel extends EntityModel<SoulCourierEntity> {
    public enum Material {
        SKELETON(new ResourceLocation("minecraft", "textures/entity/skeleton/skeleton.png")),
        CHEST(new ResourceLocation("minecraft", "textures/entity/chest/ender.png")),
        CLOAK(texture("cloak")), STRAP(texture("strap")), SOUL(texture("soul"));

        public final ResourceLocation texture;
        Material(ResourceLocation texture) { this.texture = texture; }
        private static ResourceLocation texture(String name) {
            return new ResourceLocation(SoulCatcherPlus.ID, "textures/entity/soul_courier/" + name + ".png");
        }
    }

    private final Part root, head, leftLeg, rightLeg, lid, lock;

    public SoulCourierModel() {
        Map<String, Part> parts = SoulCourierGeometry.create();
        root = parts.get("root");
        head = parts.get("head");
        leftLeg = parts.get("left_leg");
        rightLeg = parts.get("right_leg");
        lid = parts.get("chest_lid");
        lock = parts.get("chest_lock");
    }

    @Override public void setupAnim(SoulCourierEntity courier, float swing, float amount,
            float age, float yaw, float pitch) {
        head.rx = -MathHelper.clamp(pitch, -20, 25);
        head.ry = MathHelper.clamp(yaw, -45, 45);
        leftLeg.rx = MathHelper.cos(swing * 0.6662f) * 40 * amount;
        rightLeg.rx = -leftLeg.rx;
        float progress = 1 - courier.lidProgress(MathHelper.clamp(age - courier.tickCount, 0, 1));
        lid.rx = lock.rx = -90 * (1 - progress * progress * progress);
    }

    @Override public void renderToBuffer(MatrixStack pose, IVertexBuilder buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        render(Material.SKELETON, pose, buffer, light, overlay, red, green, blue, alpha);
    }

    public void render(Material material, MatrixStack pose, IVertexBuilder buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        pose.pushPose();
        // EntityModel is Y-down with its feet at 24; the authored geometry is Y-up at zero.
        pose.translate(0, 1.5, 0);
        pose.scale(1, -1, 1);
        root.render(material, pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }

    // Keep the generated Courier geometry API while sharing the cuboid renderer.
    static final class Part extends CuboidModelPart<Material> {
        Part(float x, float y, float z, float rx, float ry, float rz) {
            super(x, y, z, rx, ry, rz);
        }
    }
}
