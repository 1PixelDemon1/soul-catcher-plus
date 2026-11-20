// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.data;

import net.minecraft.nbt.CompoundTag;

/** Small immutable owner preference, safe to use on either physical side. */
public record SoulAppearance(int color, float opacity) {
    public static final int DEFAULT_COLOR = 0x59D9FF;
    public static final float DEFAULT_OPACITY = .42f, MIN_OPACITY = .1f;
    public static final SoulAppearance DEFAULT = new SoulAppearance(DEFAULT_COLOR, DEFAULT_OPACITY);

    public boolean valid() {
        return color >= 0 && color <= 0xFFFFFF && Float.isFinite(opacity) && opacity >= MIN_OPACITY && opacity <= 1;
    }
    public static SoulAppearance clamped(int color, float opacity) {
        return new SoulAppearance(color & 0xFFFFFF,
            Float.isFinite(opacity) ? Math.max(MIN_OPACITY, Math.min(1, opacity)) : DEFAULT_OPACITY);
    }
    public float red() { return color == DEFAULT_COLOR ? .35f : (color >> 16 & 255) / 255f; }
    public float green() { return color == DEFAULT_COLOR ? .85f : (color >> 8 & 255) / 255f; }
    public float blue() { return (color & 255) / 255f; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Color", color);
        tag.putFloat("Opacity", opacity);
        return tag;
    }
    public static SoulAppearance load(CompoundTag tag) {
        return clamped(tag.contains("Color") ? tag.getInt("Color") : DEFAULT_COLOR,
            tag.contains("Opacity") ? tag.getFloat("Opacity") : DEFAULT_OPACITY);
    }
}
