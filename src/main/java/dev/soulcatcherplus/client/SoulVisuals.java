// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.soulcatcherplus.api.client.SoulVisualProvider;
import dev.soulcatcherplus.config.SoulClientConfig;
import dev.soulcatcherplus.mixin.client.SoulRenderTypeAccess;
import dev.soulcatcherplus.mixin.client.SoulRenderSetupAccess;
import dev.soulcatcherplus.mixin.client.SoulTextureBindingAccess;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** Capture appearance at submission time, before queued geometry is consumed. */
public final class SoulVisuals {
    @FunctionalInterface interface VertexFade { float opacity(float x,float y,float z); }
    record Appearance(Identifier type,int color,VertexFade fade) {}
    private static final Map<Identifier,SoulVisualProvider> PROVIDERS=new HashMap<>();
    public static void register(Identifier type,SoulVisualProvider provider) { PROVIDERS.put(type,provider); }
    public static boolean hideDepartingSoul(EntityRenderState state) {
        return !SoulDissolves.renderingEcho() && SoulDissolves.contains(((SoulRenderState)state).soulcatcherplus$uuid());
    }
    public static SubmitNodeCollector companionNodes(EntityRenderState state,SubmitNodeCollector nodes) {
        if(SoulDissolves.renderingEcho()) return nodes;
        var raw=nodes instanceof SoulSubmitNodes spectral?spectral.root():nodes;
        if(!((SoulRenderState)state).soulcatcherplus$isSoul()) return raw;
        return new SoulSubmitNodes(raw,appearance(state,SoulClientConfig.opacity(),null));
    }
    static Appearance appearance(EntityRenderState state,float opacity,VertexFade fade) {
        return new Appearance(state.entityType.builtInRegistryHolder().key().identifier(),
            ARGB.colorFromFloat(opacity,SoulClientConfig.red(),SoulClientConfig.green(),SoulClientConfig.blue()),fade);
    }
    static RenderType remap(Appearance appearance,RenderType original) {
        var setup=((SoulRenderTypeAccess)original).soulcatcherplus$state();
        var binding=((SoulRenderSetupAccess)(Object)setup).soulcatcherplus$textures().get("Sampler0");
        if(binding==null) return original;
        Identifier texture=((SoulTextureBindingAccess)binding).soulcatcherplus$location();
        var provider=PROVIDERS.get(appearance.type());
        if(provider!=null) return provider.remap(original,texture);
        if(original==RenderTypes.entitySolid(texture) || original==RenderTypes.entityCutoutCull(texture)
                || original==RenderTypes.entityCutout(texture) || original==RenderTypes.entityCutout(texture,false)
                || original==RenderTypes.entityTranslucentCull(texture)) return RenderTypes.entityTranslucent(texture);
        if(original==RenderTypes.eyes(texture)) return RenderTypes.entityTranslucentEmissive(texture);
        return original;
    }
    static int color(Appearance appearance,int original) { return ARGB.multiply(original,appearance.color()); }
    static VertexConsumer tint(VertexConsumer delegate,Appearance appearance) {
        int tint=appearance.color();
        return new VertexConsumer() {
            private float fade=1;
            public VertexConsumer addVertex(float x,float y,float z) {
                fade=appearance.fade()==null?1:Mth.clamp(appearance.fade().opacity(x,y,z),0,1);
                delegate.addVertex(x,y,z);return this;
            }
            public VertexConsumer setColor(int r,int g,int b,int a) {
                delegate.setColor(r*ARGB.red(tint)/255,g*ARGB.green(tint)/255,b*ARGB.blue(tint)/255,(int)(a*ARGB.alpha(tint)/255f*fade));return this;
            }
            public VertexConsumer setColor(int color) { return setColor(ARGB.red(color),ARGB.green(color),ARGB.blue(color),ARGB.alpha(color)); }
            public VertexConsumer setUv(float u,float v) { delegate.setUv(u,v);return this; }
            public VertexConsumer setUv1(int u,int v) { delegate.setUv1(u,v);return this; }
            public VertexConsumer setUv2(int u,int v) { delegate.setUv2(u,v);return this; }
            public VertexConsumer setUv3(float u,float v) { delegate.setUv3(u,v);return this; }
            public VertexConsumer setNormal(float x,float y,float z) { delegate.setNormal(x,y,z);return this; }
            public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width);return this; }
        };
    }
    private SoulVisuals() {}
}
