// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

/** Client-only access to the native Wither head snapshots, without per-frame state. */
public interface WitherHeadInterpolation {
    float soulcatcherplus$headYaw(int head, float partialTick);
    float soulcatcherplus$headPitch(int head, float partialTick);
}
