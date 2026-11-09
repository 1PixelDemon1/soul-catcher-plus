// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.renderer.rendertype.RenderType")
public interface SoulRenderTypeAccess {
    @Accessor("state") net.minecraft.client.renderer.rendertype.RenderSetup soulcatcherplus$state();
}
