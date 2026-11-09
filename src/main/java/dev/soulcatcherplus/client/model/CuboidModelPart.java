// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import com.mojang.math.Vector3f;

/** Small shared renderer for authored Y-up cuboids with explicit per-face UVs. */
class CuboidModelPart<M> {
    private final float x, y, z;
    float rx, ry, rz;
    float offsetY;
    final List<CuboidModelPart<M>> children = new ArrayList<>();
    private final List<Cube<M>> cubes = new ArrayList<>();

    CuboidModelPart(float x, float y, float z, float rx, float ry, float rz) {
        this.x = x; this.y = y; this.z = z;
        this.rx = rx; this.ry = ry; this.rz = rz;
    }

    void cube(M material, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv) {
        cube(material, x0, y0, z0, x1, y1, z1, uv, 63);
    }

    /** Bits select north, south, west, east, down, up; mixed-material faces render exactly once. */
    void cube(M material, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int faces) {
        cubes.add(new Cube<>(material, x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16, uv, faces));
    }

    void render(M material, PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.translate(x / 16, (y + offsetY) / 16, z / 16);
        pose.mulPose(Vector3f.ZP.rotationDegrees(rz));
        pose.mulPose(Vector3f.YP.rotationDegrees(ry));
        pose.mulPose(Vector3f.XP.rotationDegrees(rx));
        for (Cube<M> cube : cubes) {
            if (cube.material == material) cube.render(pose.last(), buffer, light, overlay, red, green, blue, alpha);
        }
        for (CuboidModelPart<M> child : children) child.render(material, pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }

    private static final class Cube<M> {
        private final M material;
        // Precomputed XYZ, UV and normal; rendering does not allocate face arrays.
        private final List<float[]> vertices = new ArrayList<>(24);
        private final int faces;

        Cube(M material, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int faces) {
            this.material = material;
            this.faces = faces;
            face(uv, 0, 0,0,-1, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
            face(uv, 4, 0,0,1, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
            face(uv, 8, -1,0,0, x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0);
            face(uv, 12, 1,0,0, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
            face(uv, 16, 0,-1,0, x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
            face(uv, 20, 0,1,0, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        }

        private void face(float[] uv, int offset, float nx, float ny, float nz, float... xyz) {
            if ((faces & (1 << (offset / 4))) == 0) return;
            float u0 = uv[offset], v0 = uv[offset + 1], u1 = uv[offset + 2], v1 = uv[offset + 3];
            float[][] coords = ny == 0 ? new float[][] {{u1,v1}, {u1,v0}, {u0,v0}, {u0,v1}}
                : new float[][] {{u0,v0}, {u0,v1}, {u1,v1}, {u1,v0}};
            // Compensate for the Y reflection used to render Y-up Blockbench geometry.
            for (int i = 3; i >= 0; i--) vertices.add(new float[] {
                xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], coords[i][0], coords[i][1], nx, ny, nz});
        }

        void render(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            for (float[] v : vertices) buffer.vertex(pose.pose(), v[0], v[1], v[2])
                .color(red, green, blue, alpha).uv(v[3], v[4]).overlayCoords(overlay).uv2(light)
                .normal(pose.normal(), v[5], v[6], v[7]).endVertex();
        }
    }
}
