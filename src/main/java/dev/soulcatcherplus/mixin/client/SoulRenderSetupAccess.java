// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.renderer.rendertype.RenderSetup")
public interface SoulRenderSetupAccess {
    @Accessor("textures") java.util.Map<String, ?> soulcatcherplus$textures();
}
