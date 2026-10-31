// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.api.client.SoulVisualProvider;
import dev.soulcatcherplus.companion.CompanionBrain;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;

public final class SoulVisuals {
    @FunctionalInterface interface VertexFade { float opacity(double x, double y, double z); }
    private static final Map<ResourceLocation, SoulVisualProvider> PROVIDERS = new HashMap<>();
    public static void register(ResourceLocation entityType, SoulVisualProvider provider) {
        PROVIDERS.put(entityType, provider);
    }
    /** Called by the client dispatcher hook; the echo replaces the departing world model. */
    public static boolean hideDepartingSoul(Entity entity) {
        return !SoulDissolves.renderingEcho() && SoulDissolves.contains(entity.getUUID());
    }
    /** Adapts the native renderer's buffers without rerendering or depending on its implementation. */
    public static <T extends Entity> IRenderTypeBuffer companionBuffers(T entity, IRenderTypeBuffer buffers) {
        // Dissolve echoes already supply their own tint and per-vertex fade.
        if (SoulDissolves.renderingEcho()) return buffers;
        // A renderer can dispatch another entity using its supplied source. Apply that entity's
        // texture/provider from the raw source, rather than stacking the parent's color and alpha.
        IRenderTypeBuffer source = buffers instanceof CompanionBuffers spectral ? spectral.delegate() : buffers;
        if (!CompanionBrain.isSoul(entity)) return source;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        ResourceLocation texture = renderer.getTextureLocation(entity);
        ResourceLocation type = entity.getType().getRegistryName();
        return new CompanionBuffers(source, type, texture);
    }
    @com.github.bsideup.jabel.Desugar
    private record CompanionBuffers(IRenderTypeBuffer delegate, ResourceLocation type,
            ResourceLocation texture) implements IRenderTypeBuffer {
        @Override public IVertexBuilder getBuffer(RenderType original) {
            return tint(delegate.getBuffer(remap(type, texture, original)));
        }
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
    static IVertexBuilder tint(IVertexBuilder delegate) {
        return tint(delegate, 0.42f);
    }
    static IVertexBuilder tint(IVertexBuilder delegate, float opacity) {
        return tint(delegate, opacity, null);
    }
    static IVertexBuilder tint(IVertexBuilder delegate, float opacity, VertexFade fade) {
        float alpha = net.minecraft.util.math.MathHelper.clamp(opacity, 0, 1);
        return new IVertexBuilder() {
            private float vertexAlpha = 1;
            public IVertexBuilder vertex(double x, double y, double z) {
                vertexAlpha = fade == null ? 1 : net.minecraft.util.math.MathHelper.clamp(fade.opacity(x, y, z), 0, 1);
                delegate.vertex(x, y, z); return this;
            }
            public IVertexBuilder color(int r, int g, int b, int a) {
                delegate.color((int)(r * 0.35f), (int)(g * 0.85f), b, (int)(a * alpha * vertexAlpha)); return this;
            }
            public IVertexBuilder uv(float u, float v) { delegate.uv(u, v); return this; }
            public IVertexBuilder overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
            public IVertexBuilder uv2(int u, int v) { delegate.uv2(u, v); return this; }
            public IVertexBuilder normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
            public void endVertex() { delegate.endVertex(); }
        };
    }
    private SoulVisuals() {}
}
