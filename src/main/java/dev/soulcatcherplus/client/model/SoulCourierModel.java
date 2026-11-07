// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulCourierEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

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

    static final class Part {
        private final float x, y, z;
        float rx, ry, rz;
        final List<Part> children = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();

        Part(float x, float y, float z, float rx, float ry, float rz) {
            this.x = x; this.y = y; this.z = z;
            this.rx = rx; this.ry = ry; this.rz = rz;
        }

        void cube(Material material, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
            cubes.add(new Cube(material, x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16, uv));
        }

        void render(Material material, MatrixStack pose, IVertexBuilder buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            pose.pushPose();
            pose.translate(x / 16, y / 16, z / 16);
            pose.mulPose(Vector3f.ZP.rotationDegrees(rz));
            pose.mulPose(Vector3f.YP.rotationDegrees(ry));
            pose.mulPose(Vector3f.XP.rotationDegrees(rx));
            for (Cube cube : cubes) {
                if (cube.material == material) cube.render(pose.last(), buffer, light, overlay, red, green, blue, alpha);
            }
            for (Part child : children) child.render(material, pose, buffer, light, overlay, red, green, blue, alpha);
            pose.popPose();
        }
    }

    private static final class Cube {
        final Material material;
        // Precomputed XYZ, UV and normal; no face arrays are allocated during rendering.
        final List<float[]> vertices = new ArrayList<>(24);

        Cube(Material material, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
            this.material = material;
            face(uv, 0, 0,0,-1, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
            face(uv, 4, 0,0,1, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
            face(uv, 8, -1,0,0, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0);
            face(uv, 12, 1,0,0, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
            face(uv, 16, 0,-1,0, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
            face(uv, 20, 0,1,0, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        }

        private void face(float[] uv, int offset, float nx, float ny, float nz, float... xyz) {
            float u0 = uv[offset], v0 = uv[offset + 1], u1 = uv[offset + 2], v1 = uv[offset + 3];
            float[][] coords = ny == 0 ? new float[][] {{u1,v1}, {u1,v0}, {u0,v0}, {u0,v1}}
                : new float[][] {{u0,v0}, {u0,v1}, {u1,v1}, {u1,v0}};
            // Compensate for the Y reflection in render(), keeping outward-facing winding.
            for (int i = 3; i >= 0; i--) vertices.add(new float[] {
                xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], coords[i][0], coords[i][1], nx, ny, nz});
        }

        void render(MatrixStack.Entry pose, IVertexBuilder buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            for (float[] v : vertices) buffer.vertex(pose.pose(), v[0], v[1], v[2])
                .color(red, green, blue, alpha).uv(v[3], v[4]).overlayCoords(overlay).uv2(light)
                .normal(pose.normal(), v[5], v[6], v[7]).endVertex();
        }
    }
}
