// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.entity.SoulShamanEntity;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The approved cuboid model, retaining its authored dimensions, materials and pivots. */
public final class SoulShamanModel extends EntityModel<SoulShamanEntity> {
    public enum Material {
        CLOTH, BONE, BONE_LIGHT, BONE_SHADE, WOOD, WOOD_LIGHT, WOOD_SHADE, CORD, CAVITY, CYAN_TRIM,
        SOUL_BLUE(true), SOUL_LIGHT(true), SOUL_MIDDLE(true);

        public final ResourceLocation texture;
        public final boolean emissive;
        Material() { this(false); }
        Material(boolean emissive) {
            this.emissive = emissive;
            texture = new ResourceLocation(SoulCatcherPlus.ID,
                "textures/entity/soul_shaman/" + name().toLowerCase(java.util.Locale.ROOT) + ".png");
        }
    }

    private static final String[] HEM_NAMES = {"front_left", "front_right", "front_center",
        "back_left", "back_right", "back_center", "side_-1", "side_1"};
    private final CuboidModelPart<Material> root, head, rightArm, leftArm, staff, charm, wisp;
    private final CuboidModelPart<Material>[] hems;
    private float hover;

    @SuppressWarnings("unchecked")
    public SoulShamanModel() {
        Map<String, CuboidModelPart<Material>> parts = SoulShamanGeometry.create();
        root = parts.get("root");
        head = parts.get("head");
        rightArm = parts.get("arm_right");
        leftArm = parts.get("arm_left");
        staff = parts.get("staff");
        charm = parts.get("charm");
        wisp = parts.get("wisp");
        hems = (CuboidModelPart<Material>[]) new CuboidModelPart<?>[HEM_NAMES.length];
        for (int i = 0; i < hems.length; i++) hems[i] = parts.get("hem_" + HEM_NAMES[i]);
    }

    @Override public void setupAnim(SoulShamanEntity shaman, float swing, float amount,
            float age, float yaw, float pitch) {
        head.rx = -Mth.clamp(pitch, -12, 12);
        head.ry = Mth.clamp(yaw, -25, 25);
        float bob = Mth.sin(age * 0.1f);
        hover = bob * 0.02f;
        for (int i = 0; i < hems.length; i++) {
            CuboidModelPart<Material> hem = hems[i];
            hem.offsetY = bob * (i % 3 == 2 ? 0.18f : 0.28f);
            hem.rx = i < 6 ? bob * (i < 3 ? 3 : -3) : 0;
            hem.rz = i < 6 ? 0 : bob * (i == 6 ? -3 : 3);
        }
        wisp.ry = bob * 5;
        // The server starts each support cast; this also works for observers joining mid-gesture.
        float phase = shaman.castPhase(age);
        float raise = phase >= SoulShamanEntity.CAST_DURATION_TICKS ? 0
            : phase < 12 ? smooth(phase / 12) : 1 - smooth((phase - 24) / 12);
        float sweep = raise * (2 * smooth((phase - 12) / 12) - 1);
        rightArm.rx = 25 * raise;
        rightArm.ry = -18 * sweep;
        rightArm.rz = 28 * raise;
        leftArm.rx = 8 * raise;
        leftArm.rz = -6 * raise;
        staff.rx = -8 * raise;
        staff.ry = 12 * sweep;
        staff.rz = -10 * raise;
        charm.rx = Mth.sin(age * 0.15f) * 3 + 8 * sweep;
    }

    private static float smooth(float progress) {
        float t = Mth.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        render(Material.CLOTH, pose, buffer, light, overlay, red, green, blue, alpha);
    }

    public void render(Material material, PoseStack pose, VertexConsumer buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.translate(0, 1.5 - hover, 0);
        pose.scale(1, -1, 1);
        root.render(material, pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }
}
