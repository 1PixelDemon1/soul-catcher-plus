// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;

import com.mojang.serialization.MapCodec;
import dev.soulcatcherplus.client.SoulCageItemRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 66 has no special-model codec registration event. Register alongside vanilla codecs. */
@Mixin(SpecialModelRenderers.class)
public abstract class SoulSpecialModelsMixin {
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> ID_MAPPER;
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void soulcatcherplus$register(CallbackInfo callback) {
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("soulcatcherplus", "soul_cage"), SoulCageItemRenderer.Unbaked.MAP_CODEC);
    }
}
