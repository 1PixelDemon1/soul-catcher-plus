// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import dev.soulcatcherplus.api.client.SoulVisualProvider;
import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.config.SoulClientConfig;
import dev.soulcatcherplus.SoulCatcherPlus;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameplateEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SoulCatcherPlus.ID, value = Dist.CLIENT)
public final class SoulVisuals {
    @FunctionalInterface interface VertexFade { float opacity(double x, double y, double z); }
    private static final Map<ResourceLocation, SoulVisualProvider> PROVIDERS = new HashMap<>();
    public static void register(ResourceLocation entityType, SoulVisualProvider provider) {
        PROVIDERS.put(entityType, provider);
    }
    /** Hide floating labels while leaving synced entity names available to maps and other mods. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void nameplate(RenderNameplateEvent event) {
        if (CompanionBrain.isSoul(event.getEntity())) event.setResult(Event.Result.DENY);
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
        return tint(delegate, SoulClientConfig.opacity());
    }
    static IVertexBuilder tint(IVertexBuilder delegate, float opacity) {
        return tint(delegate, opacity, null);
    }
    static IVertexBuilder tint(IVertexBuilder delegate, float opacity, VertexFade fade) {
        float alpha = net.minecraft.util.math.MathHelper.clamp(opacity, 0, 1);
        float red = SoulClientConfig.red(), green = SoulClientConfig.green(), blue = SoulClientConfig.blue();
        return new IVertexBuilder() {
            private float vertexAlpha = 1;
            public IVertexBuilder vertex(double x, double y, double z) {
                vertexAlpha = fade == null ? 1 : net.minecraft.util.math.MathHelper.clamp(fade.opacity(x, y, z), 0, 1);
                delegate.vertex(x, y, z); return this;
            }
            public IVertexBuilder color(int r, int g, int b, int a) {
                delegate.color((int)(r * red), (int)(g * green), (int)(b * blue), (int)(a * alpha * vertexAlpha)); return this;
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
