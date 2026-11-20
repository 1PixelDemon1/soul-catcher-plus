// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import java.util.UUID;
/** Metadata captured while the live entity is available; render queues retain no entity reference. */
public interface SoulRenderState {
    UUID soulcatcherplus$uuid();
    boolean soulcatcherplus$isSoul();
    dev.soulcatcherplus.data.SoulAppearance soulcatcherplus$appearance();
    void soulcatcherplus$capture(UUID uuid, boolean soul, dev.soulcatcherplus.data.SoulAppearance appearance);
}
