// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.vector.Vector3f;

/** Native mesh for the approved cage. Coordinates and colors come from Blockbench. */
public final class SoulCageModel {
    public enum Material { IRON, DIAMOND, NETHERITE, SUPREME }
    public enum Surface { FRAME, SOUL, GLASS }
    private final Part root;
    private static final float[] CAGE_TILT = {0, 1, 0, -1, 0};
    private static final float[] SOUL_TILT = {-5, 5, -5};

    public SoulCageModel(Material material) {
        root = SoulCageGeometry.create(material);
    }

    public void render(Surface surface, float seconds, MatrixStack pose,
            IVertexBuilder buffer, int light, int overlay) {
        root.render(surface, seconds, pose, buffer, light, overlay);
    }

    /** Linear interpolation matches the four-second Blockbench keyframes. */
    private static float sample(float seconds, float step, float[] keys) {
        float frame = seconds / step;
        int index = Math.min((int) frame, keys.length - 2);
        return keys[index] + (keys[index + 1] - keys[index]) * (frame - index);
    }

    static final class Part {
        final String name;
        final float x, y, z;
        final List<Part> children = new ArrayList<>();
        final List<Cube> cubes = new ArrayList<>();
        final float[] bob;

        Part(String name, float x, float y, float z) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            if (name.startsWith("soul_")) {
                int index = Integer.parseInt(name.substring(5)) - 1;
                bob = new float[9];
                for (int key = 0; key < bob.length; key++) {
                    bob[key] = (float) Math.sin(key * Math.PI / 4 + index * Math.PI * 2 / 3);
                }
            } else bob = null;
        }

        void cube(Surface surface, float x0, float y0, float z0, float x1, float y1, float z1,
                int north, int south, int west, int east, int down, int up) {
            cubes.add(new Cube(surface, x0, y0, z0, x1, y1, z1,
                new int[] {north, south, west, east, down, up}));
        }

        void render(Surface surface, float seconds, MatrixStack pose,
                IVertexBuilder buffer, int light, int overlay) {
            pose.pushPose();
            pose.translate(x / 16, y / 16, z / 16);
            if (name.equals("cage")) {
                float tilt = sample(seconds, 1, CAGE_TILT);
                pose.mulPose(Vector3f.XP.rotationDegrees(tilt));
                pose.mulPose(Vector3f.ZP.rotationDegrees(tilt * .8f));
            } else if (name.startsWith("orbit_")) {
                pose.mulPose(Vector3f.YP.rotationDegrees(seconds * 90));
            } else if (bob != null) {
                pose.translate(0, sample(seconds, .5f, bob) / 16, 0);
                pose.mulPose(Vector3f.ZP.rotationDegrees(sample(seconds, 2, SOUL_TILT)));
            }
            for (Cube cube : cubes) {
                if (cube.surface == surface) cube.render(pose.last(), buffer, light, overlay);
            }
            for (Part child : children) child.render(surface, seconds, pose, buffer, light, overlay);
            pose.popPose();
        }
    }

    private static final class Cube {
        final Surface surface;
        final float x0, y0, z0, x1, y1, z1;
        final int[] colors;

        Cube(Surface surface, float x0, float y0, float z0, float x1, float y1, float z1, int[] colors) {
            this.surface = surface;
            this.x0 = x0 / 16; this.y0 = y0 / 16; this.z0 = z0 / 16;
            this.x1 = x1 / 16; this.y1 = y1 / 16; this.z1 = z1 / 16;
            this.colors = colors;
        }

        void render(MatrixStack.Entry pose, IVertexBuilder buffer, int light, int overlay) {
            face(pose, buffer, light, overlay, colors[0], 0, 0, -1,
                x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
            face(pose, buffer, light, overlay, colors[1], 0, 0, 1,
                x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
            face(pose, buffer, light, overlay, colors[2], -1, 0, 0,
                x0,y0,z1, x0,y1,z1, x0,y1,z0, x0,y0,z0);
            face(pose, buffer, light, overlay, colors[3], 1, 0, 0,
                x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
            face(pose, buffer, light, overlay, colors[4], 0, -1, 0,
                x0,y0,z1, x0,y0,z0, x1,y0,z0, x1,y0,z1);
            face(pose, buffer, light, overlay, colors[5], 0, 1, 0,
                x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        }

        private static void face(MatrixStack.Entry pose, IVertexBuilder buffer, int light, int overlay,
                int rgba, float nx, float ny, float nz,
                float ax, float ay, float az, float bx, float by, float bz,
                float cx, float cy, float cz, float dx, float dy, float dz) {
            vertex(pose, buffer, light, overlay, rgba, nx,ny,nz, ax,ay,az, 0,0);
            vertex(pose, buffer, light, overlay, rgba, nx,ny,nz, bx,by,bz, 0,1);
            vertex(pose, buffer, light, overlay, rgba, nx,ny,nz, cx,cy,cz, 1,1);
            vertex(pose, buffer, light, overlay, rgba, nx,ny,nz, dx,dy,dz, 1,0);
        }

        private static void vertex(MatrixStack.Entry pose, IVertexBuilder buffer, int light, int overlay,
                int rgba, float nx, float ny, float nz, float x, float y, float z, float u, float v) {
            buffer.vertex(pose.pose(), x, y, z)
                .color((rgba >>> 24) & 255, (rgba >>> 16) & 255, (rgba >>> 8) & 255, rgba & 255)
                .uv(u, v).overlayCoords(overlay).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
        }
    }
}
