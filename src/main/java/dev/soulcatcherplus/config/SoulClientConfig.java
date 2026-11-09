// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client appearance preferences; no Minecraft client classes are loaded on a server. */
public final class SoulClientConfig {
    public static final int DEFAULT_COLOR = 0x59D9FF;
    public static final float DEFAULT_OPACITY = 0.42f;
    public static final double MIN_OPACITY = 0.1;
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue COLOR;
    private static final ForgeConfigSpec.DoubleValue OPACITY;
    // The picker previews changes in memory and writes them once when closed.
    private static int previewColor = -1;
    private static float previewOpacity;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("appearance");
        COLOR = builder.comment("RGB tint of summoned souls, stored as a decimal RGB integer.")
            .defineInRange("color", DEFAULT_COLOR, 0, 0xFFFFFF);
        OPACITY = builder.comment("Opacity of summoned souls: 0.1 is the minimum, 1 is opaque.")
            .defineInRange("opacity", 0.42, MIN_OPACITY, 1);
        builder.pop();
        SPEC = builder.build();
    }

    public static int color() { return previewColor < 0 ? COLOR.get() : previewColor; }
    public static float opacity() {
        return Math.max((float) MIN_OPACITY, previewColor < 0 ? OPACITY.get().floatValue() : previewOpacity);
    }
    public static float red() { return color() == DEFAULT_COLOR ? 0.35f : (color() >> 16 & 255) / 255f; }
    public static float green() { return color() == DEFAULT_COLOR ? 0.85f : (color() >> 8 & 255) / 255f; }
    public static float blue() { return (color() & 255) / 255f; }

    public static void preview(int color, float opacity) {
        previewColor = color & 0xFFFFFF;
        previewOpacity = Math.max((float) MIN_OPACITY, Math.min(1, opacity));
    }

    public static void savePreview() {
        if (previewColor < 0) return;
        COLOR.set(previewColor);
        OPACITY.set(Math.round(previewOpacity * 1000) / 1000.0);
        SPEC.save();
        previewColor = -1;
    }

    private SoulClientConfig() {}
}
