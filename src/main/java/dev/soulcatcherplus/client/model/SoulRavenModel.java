// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.entity.SoulRavenEntity;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class SoulRavenModel {
    private static final float RADIANS = (float) Math.PI / 180.0f;
    private static final float WING_PERIOD = 16.0f;
    private final ModelPart root, body, head, leftWing, rightWing, tail, leftLeg, rightLeg;
    private final float restingBodyY;

    public SoulRavenModel() {
        Map<String, ModelPart> bones = SoulRavenGeometry.create(this);
        root = bones.get("raven");
        body = bones.get("body");
        head = bones.get("head");
        leftWing = bones.get("wing_left");
        rightWing = bones.get("wing_right");
        tail = bones.get("tail");
        leftLeg = bones.get("leg_left");
        rightLeg = bones.get("leg_right");
        restingBodyY = body.y;
    }

    public void setupAnim(float flight, float age, float headYaw, float headPitch) {
        float flap = Mth.sin(age * ((float) Math.PI * 2.0f / WING_PERIOD));
        float wingAngle = (68.5f + 43.5f * flap) * RADIANS * flight;
        leftWing.zRot = -wingAngle;
        rightWing.zRot = wingAngle;
        leftLeg.xRot = rightLeg.xRot = 75.0f * RADIANS * flight;
        head.xRot = (Mth.clamp(headPitch, -30.0f, 30.0f) + 6.0f * flight) * RADIANS;
        head.yRot = Mth.clamp(Mth.wrapDegrees(headYaw), -55.0f, 55.0f) * RADIANS;
        // The longer hanging tail lifts behind the body when the wings open.
        tail.xRot = (54.0f + 2.0f * flap) * RADIANS * flight
            - (1.0f - flight) * RADIANS * (1.0f + Mth.sin(age * 0.105f));
        body.y = restingBodyY - flight * (0.45f + 0.25f * flap)
            - (1.0f - flight) * 0.04f * (1.0f + Mth.sin(age * 0.105f));
    }

    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light,
            int overlay, float red, float green, float blue, float alpha) {
        root.render(pose, buffer, light, overlay, net.minecraft.util.ARGB.colorFromFloat(alpha,red,green,blue));
    }
}
