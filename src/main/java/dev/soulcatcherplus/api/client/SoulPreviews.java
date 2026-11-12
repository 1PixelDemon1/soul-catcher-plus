// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.api.client;

import net.minecraft.entity.Entity;

/** Client-side identity for detached collection models, never world entities. */
public final class SoulPreviews {
    private static final String MARKER = "scp_collection_preview";

    /** Mark a detached entity so renderer integrations can safely isolate GUI poses. */
    public static void mark(Entity entity) { entity.getPersistentData().putBoolean(MARKER, true); }

    public static boolean isPreview(Entity entity) {
        return entity.getPersistentData().getBoolean(MARKER);
    }

    private SoulPreviews() {}
}
