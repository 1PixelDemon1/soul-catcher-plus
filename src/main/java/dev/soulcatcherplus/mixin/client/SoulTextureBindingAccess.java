// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface SoulTextureBindingAccess {
    @Accessor("location") net.minecraft.resources.Identifier soulcatcherplus$location();
}
