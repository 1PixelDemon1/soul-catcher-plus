// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

/** Small drawing surface for the PoseStack-based 1.19.2 screens. */
final class SoulGui extends GuiComponent {
    private final PoseStack pose;

    SoulGui(PoseStack pose) { this.pose = pose; }
    PoseStack pose() { return pose; }
    MultiBufferSource.BufferSource bufferSource() { return Minecraft.getInstance().renderBuffers().bufferSource(); }
    void flush() { bufferSource().endBatch(); }

    void beginClip(int left, int top, int right, int bottom) {
        flush();
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor((int)(left * scale),
            (int)(window.getHeight() - bottom * scale), (int)((right - left) * scale), (int)((bottom - top) * scale));
    }
    void endClip() { flush(); com.mojang.blaze3d.systems.RenderSystem.disableScissor(); }

    void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }
    void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        fillGradient(pose, x1, y1, x2, y2, top, bottom);
    }
    void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color);
        else font.draw(pose, text, x, y, color);
    }
    void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color);
        else font.draw(pose, text, x, y, color);
    }
    void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }
    void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }
    void drawWordWrap(Font font, Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            font.draw(pose, line, x, y, color);
            y += font.lineHeight;
        }
    }
}
