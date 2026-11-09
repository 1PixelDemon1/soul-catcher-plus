// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
/** Immutable-for-submission animation samples; queued geometry never reads a live entity. */
public final class SoulModelState extends EntityRenderState {
    public float bodyYaw, headYaw, pitch, swing, amount, flight, lid, castPhase;
    public int overlay;
}
