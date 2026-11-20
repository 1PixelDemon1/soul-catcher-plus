// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.projector;

import dev.soulcatcherplus.data.SoulAppearance;
import net.minecraft.nbt.CompoundTag;

public record ProjectorSettings(int scale, int color, int angle, int opacity, boolean rotating) {
    public static final ProjectorSettings DEFAULT = new ProjectorSettings(100, SoulAppearance.DEFAULT_COLOR, 0, 60, false);
    public boolean valid() { return scale >= 25 && scale <= 400 && color >= 0 && color <= 0xFFFFFF
        && angle >= 0 && angle < 360 && opacity >= 10 && opacity <= 100; }
    public SoulAppearance appearance() { return new SoulAppearance(color, opacity / 100f); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Scale", scale); tag.putInt("Color", color); tag.putInt("Angle", angle);
        tag.putInt("Opacity", opacity); tag.putBoolean("Rotating", rotating);
        return tag;
    }
    public static ProjectorSettings load(CompoundTag tag) {
        ProjectorSettings value = new ProjectorSettings(tag.getIntOr("Scale", 0), tag.getIntOr("Color", 0),
            tag.getIntOr("Angle", 0), tag.getIntOr("Opacity", 0), tag.getBooleanOr("Rotating", false));
        return value.valid() ? value : DEFAULT;
    }
}
