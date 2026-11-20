// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Local owner preferences, published to the server after saving; previews remain local. */
public final class SoulClientConfig {
    public static final int DEFAULT_COLOR = dev.soulcatcherplus.data.SoulAppearance.DEFAULT_COLOR;
    public static final float DEFAULT_OPACITY = dev.soulcatcherplus.data.SoulAppearance.DEFAULT_OPACITY;
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
        COLOR = builder.comment("RGB tint of your summoned souls, shared with other players by the server. Stored as a decimal RGB integer.")
            .defineInRange("color", DEFAULT_COLOR, 0, 0xFFFFFF);
        OPACITY = builder.comment("Opacity of your summoned souls, shared with other players: 0.1 is the minimum, 1 is opaque.")
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
    public static dev.soulcatcherplus.data.SoulAppearance appearance() {
        return dev.soulcatcherplus.data.SoulAppearance.clamped(color(), opacity());
    }
    public static dev.soulcatcherplus.data.SoulAppearance savedAppearance() {
        return dev.soulcatcherplus.data.SoulAppearance.clamped(COLOR.get(), OPACITY.get().floatValue());
    }

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
