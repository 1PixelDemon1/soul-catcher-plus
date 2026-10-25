// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Client-only hook for third-party renderers; register only on Dist.CLIENT. */
@FunctionalInterface
public interface SoulVisualProvider {
    RenderType remap(RenderType original, ResourceLocation bodyTexture);
}
