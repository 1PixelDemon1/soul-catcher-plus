// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import java.util.Map;
import net.minecraft.util.Mth;

public final class SoulRavenModel {
    public enum Material { BODY, EMISSIVE }
    private static final float WING_PERIOD = 16.0f;
    private final CuboidModelPart<Material> root, body, head, leftWing, rightWing, tail, leftLeg, rightLeg;
    private final float flightTailAngle;

    public SoulRavenModel(boolean longTailedTit) {
        flightTailAngle = longTailedTit ? 28.0f : 54.0f;
        Map<String, CuboidModelPart<Material>> bones = longTailedTit
            ? SoulLongTailedTitGeometry.create() : SoulRavenGeometry.create();
        root = bones.get("raven");
        body = bones.get("body");
        head = bones.get("head");
        leftWing = bones.get("wing_left");
        rightWing = bones.get("wing_right");
        tail = bones.get("tail");
        leftLeg = bones.get("leg_left");
        rightLeg = bones.get("leg_right");
    }

    public void setupAnim(float flight, float age, float headYaw, float headPitch) {
        float flap = Mth.sin(age * ((float) Math.PI * 2.0f / WING_PERIOD));
        float wingAngle = (68.5f + 43.5f * flap) * flight;
        leftWing.rz = wingAngle;
        rightWing.rz = -wingAngle;
        leftLeg.rx = rightLeg.rx = -75.0f * flight;
        head.rx = -Mth.clamp(headPitch, -30.0f, 30.0f) - 6.0f * flight;
        head.ry = Mth.clamp(headYaw, -55.0f, 55.0f);
        // The longer hanging tail lifts behind the body when the wings open.
        tail.rx = -(flightTailAngle + 2.0f * flap) * flight
            + (1.0f - flight) * (1.0f + Mth.sin(age * 0.105f));
        body.offsetY = flight * (0.45f + 0.25f * flap)
            + (1.0f - flight) * 0.04f * (1.0f + Mth.sin(age * 0.105f));
    }

    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        render(Material.BODY, pose, buffer, light, overlay, red, green, blue, alpha);
    }

    public void render(Material material, PoseStack pose, VertexConsumer buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.translate(0, 1.5, 0);
        pose.scale(1, -1, 1);
        root.render(material, pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }
}
