// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.api.client.SoulVisualProvider;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

public final class SoulVisuals {
    private static final Map<ResourceLocation, SoulVisualProvider> PROVIDERS = new HashMap<>();
    public static void register(ResourceLocation entityType, SoulVisualProvider provider) {
        PROVIDERS.put(entityType, provider);
    }
    static RenderType remap(ResourceLocation type, ResourceLocation texture, RenderType original) {
        SoulVisualProvider provider = PROVIDERS.get(type);
        if (provider != null) return provider.remap(original, texture);
        if (original == RenderType.entitySolid(texture) || original == RenderType.entityCutout(texture)
            || original == RenderType.entityCutoutNoCull(texture) || original == RenderType.entityCutoutNoCull(texture, false)
            || original == RenderType.entitySmoothCutout(texture) || original == RenderType.entityTranslucentCull(texture))
            return RenderType.entityTranslucent(texture);
        return original;
    }
    static VertexConsumer tint(VertexConsumer delegate) {
        return new VertexConsumer() {
            public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
            public VertexConsumer color(int r, int g, int b, int a) {
                delegate.color((int)(r * 0.35f), (int)(g * 0.85f), b, (int)(a * 0.42f)); return this;
            }
            public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
            public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
            public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
            public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
            public void endVertex() { delegate.endVertex(); }
            public void defaultColor(int r, int g, int b, int a) {
                delegate.defaultColor((int)(r * 0.35f), (int)(g * 0.85f), b, (int)(a * 0.42f));
            }
            public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
        };
    }
    private SoulVisuals() {}
}
