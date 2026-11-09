// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Client-only hook for third-party renderers; register only on Dist.CLIENT. */
@FunctionalInterface
public interface SoulVisualProvider {
    RenderType remap(RenderType original, Identifier bodyTexture);
}
