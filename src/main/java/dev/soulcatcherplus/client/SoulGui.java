// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.util.text.ITextComponent;

/** Small drawing surface for the MatrixStack-based 1.16.5 screens. */
final class SoulGui extends AbstractGui {
    private final MatrixStack pose;

    SoulGui(MatrixStack pose) { this.pose = pose; }
    MatrixStack pose() { return pose; }
    IRenderTypeBuffer.Impl bufferSource() { return Minecraft.getInstance().renderBuffers().bufferSource(); }
    void flush() { bufferSource().endBatch(); }
    void enableScissor(int left, int top, int right, int bottom) {
        flush();
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor((int)(left * scale),
            (int)(window.getHeight() - bottom * scale), (int)((right - left) * scale), (int)((bottom - top) * scale));
    }
    void disableScissor() { flush(); com.mojang.blaze3d.systems.RenderSystem.disableScissor(); }

    void fill(int x1, int y1, int x2, int y2, int color) {
        AbstractGui.fill(pose, x1, y1, x2, y2, color);
    }
    void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        fillGradient(pose, x1, y1, x2, y2, top, bottom);
    }
    void fillHorizontalGradient(int x1, int y1, int x2, int y2, int left, int right) {
        pose.pushPose();
        pose.translate(x2, y1, 0);
        pose.mulPose(net.minecraft.util.math.vector.Vector3f.ZP.rotationDegrees(90));
        fillGradient(0, 0, y2 - y1, x2 - x1, right, left);
        pose.popPose();
    }
    void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
    void drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color);
        else font.draw(pose, text, x, y, color);
    }
    void drawString(FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color);
        else font.draw(pose, text, x, y, color);
    }
    void drawCenteredString(FontRenderer font, String text, int x, int y, int color) {
        AbstractGui.drawCenteredString(pose, font, text, x, y, color);
    }
    void drawCenteredString(FontRenderer font, ITextComponent text, int x, int y, int color) {
        AbstractGui.drawCenteredString(pose, font, text, x, y, color);
    }
    void drawWordWrap(FontRenderer font, ITextComponent text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            font.draw(pose, line, x, y, color);
            y += font.lineHeight;
        }
    }
}
