// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.client.SoulGui;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.soulcatcherplus.config.SoulClientConfig;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Local appearance editor. Changes are previewed live and saved once on exit. */
final class SoulAppearanceScreen extends Screen {
    private static final int W = 360, H = 280;
    private static final float MIN_OPACITY = (float) SoulClientConfig.MIN_OPACITY;
    private final SoulScreen parent;
    private final SoulPreview preview = new SoulPreview(true);
    private UUID entry;
    private float hue, saturation, brightness, opacity;
    private float scale, originX, originY;
    private EditBox hex;
    private boolean updatingHex;
    private int ticks;

    SoulAppearanceScreen(SoulScreen parent, UUID entry) {
        super(text("appearance"));
        this.parent = parent;
        this.entry = entry;
        opacity = SoulClientConfig.opacity();
        loadColor(SoulClientConfig.color());
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.soulcatcherplus." + key, args);
    }
    private int color() { return java.awt.Color.HSBtoRGB(hue % 1, saturation, brightness) & 0xFFFFFF; }
    private void loadColor(int color) {
        float[] hsv = java.awt.Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
        if (hsv[1] > 0) hue = hsv[0];
        saturation = hsv[1];
        brightness = hsv[2];
    }
    private void changed() {
        SoulClientConfig.preview(color(), opacity);
        updatingHex = true;
        try { hex.setValue(String.format(Locale.ROOT, "%06X", color())); }
        finally { updatingHex = false; }
    }
    private void hexChanged(String value) {
        if (updatingHex) return;
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (digits.length() == 6) {
            loadColor(Integer.parseInt(digits, 16));
            SoulClientConfig.preview(color(), opacity);
        }
    }
    private boolean validHex() { return hex.getValue().matches("#?[0-9a-fA-F]{6}"); }

    @Override protected void init() {
        scale = Math.max(0.25f, Math.min(1.5f, Math.min((width - 12f) / W, (height - 12f) / H)));
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        addRenderableWidget(new ColorControl(18, 46, 156, 110, Control.SATURATION));
        addRenderableWidget(new ColorControl(184, 46, 14, 110, Control.HUE));
        hex = addRenderableWidget(new EditBox(font, 20, 179, 176, 12, text("appearance_hex")));
        hex.setBordered(false);
        hex.setTextColor(SoulTheme.LIGHT);
        hex.setMaxLength(7);
        hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setValue(String.format(Locale.ROOT, "%06X", color()));
        hex.setResponder(this::hexChanged);
        addRenderableWidget(new ColorControl(18, 221, 180, 12, Control.OPACITY));
        addRenderableWidget(new SoulButton(18, 247, 156, 20, text("appearance_reset"), SoulButton.Style.SUBTLE, () -> {
            loadColor(SoulClientConfig.DEFAULT_COLOR);
            opacity = SoulClientConfig.DEFAULT_OPACITY;
            changed();
        }));
        addRenderableWidget(new SoulButton(184, 247, 158, 20, text("appearance_done"), SoulButton.Style.PRIMARY, this::onClose));
        preview.request(entry, ticks);
    }

    void update(SoulNetwork.State state) {
        parent.update(state);
        if (entry != null && state.rows().stream().noneMatch(row -> entry.equals(row.id()))) {
            entry = null;
            preview.clear();
        }
    }
    void receivePreview(SoulNetwork.Preview packet) {
        if (entry != null && entry.equals(packet.entry())) preview.receive(packet);
    }
    @Override public void tick() {
        ticks++;
        hex.tick();
        preview.tick();
        preview.request(entry, ticks);
        if (ticks % 20 == 0) SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.Request(SoulNetwork.Action.REFRESH, null));
    }

    @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
        SoulGui g = new SoulGui(pose);
        g.fillGradient(0, 0, width, height, 0xEB030810, 0xF2071019);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        SoulTheme.panel(g, 0, 0, W, H);
        SoulTheme.palette(g, 24, 23);
        g.drawString(font, title, 40, 18, SoulTheme.LIGHT, false);
        g.fill(18, 36, 342, 37, SoulTheme.BORDER);
        g.drawString(font, text("appearance_hex"), 18, 163, SoulTheme.MUTED, false);
        g.fill(18, 175, 198, 195, SoulTheme.INK);
        g.renderOutline(18, 175, 180, 20, validHex() ? (hex.isFocused() ? SoulTheme.TEAL : SoulTheme.BORDER) : SoulTheme.RED);
        g.drawString(font, text("appearance_opacity", Math.round(opacity * 100)), 18, 206, SoulTheme.MUTED, false);
        renderPreview(g, partialTick);
        g.drawWordWrap(font, text("appearance_local"), 214, 206, 128, SoulTheme.MUTED);
        super.render(g.pose(), (int)localX(mouseX), (int)localY(mouseY), partialTick);
        g.pose().popPose();
    }

    private void renderPreview(SoulGui g, float partialTick) {
        for (int y = 46; y < 195; y += 16) for (int x = 214; x < 342; x += 16)
            g.fill(x, y, Math.min(x + 16, 342), Math.min(y + 16, 195),
                ((x - 214) / 16 + (y - 46) / 16) % 2 == 0 ? 0xFF142932 : 0xFF1B333C);
        g.renderOutline(214, 46, 128, 149, SoulTheme.BORDER);
        g.enableScissor((int)Math.ceil(originX + 215 * scale), (int)Math.ceil(originY + 47 * scale),
            (int)Math.floor(originX + 341 * scale), (int)Math.floor(originY + 194 * scale));
        try {
            if (preview.available(entry)) preview.render(g, entry, 278, 179, 124, 142, partialTick);
            else {
                SoulTheme.diamond(g, 278, 106, 22, Math.round(opacity * 255) << 24 | color());
                g.drawWordWrap(font, text(entry == null ? "appearance_sample" : preview.failed(entry)
                    ? "preview_unavailable" : "preview_loading"), 226, 145, 104, SoulTheme.MUTED);
            }
        } finally { g.disableScissor(); }
    }

    private enum Control { SATURATION, HUE, OPACITY }

    /** Native focus/drag handling, with arrow-key adjustments for keyboard use. */
    private final class ColorControl extends AbstractWidget {
        private final Control control;
        ColorControl(int x, int y, int width, int height, Control control) {
            super(x, y, width, height, text(control == Control.OPACITY ? "appearance_opacity_control"
                : control == Control.HUE ? "appearance_hue" : "appearance_saturation"));
            this.control = control;
        }
        @Override public void onClick(double mouseX, double mouseY) { move(mouseX, mouseY); }
        @Override protected void onDrag(double mouseX, double mouseY, double dx, double dy) { move(mouseX, mouseY); }
        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { defaultButtonNarrationText(output); }
        private void move(double mouseX, double mouseY) {
            float horizontal = Mth.clamp((float)(mouseX - x) / (width - 1), 0, 1);
            float vertical = Mth.clamp((float)(mouseY - y) / (height - 1), 0, 1);
            switch (control) {
                case SATURATION -> { saturation = horizontal; brightness = 1 - vertical; }
                case HUE -> hue = vertical;
                case OPACITY -> opacity = Mth.lerp(horizontal, MIN_OPACITY, 1);
            }
            changed();
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT
                    && key != GLFW.GLFW_KEY_UP && key != GLFW.GLFW_KEY_DOWN) return false;
            float step = key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP ? 0.01f : -0.01f;
            switch (control) {
                case SATURATION -> {
                    if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)
                        saturation = Mth.clamp(saturation + step, 0, 1);
                    else brightness = Mth.clamp(brightness + step, 0, 1);
                }
                case HUE -> hue = Mth.clamp(hue + (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN ? -step : step), 0, 1);
                case OPACITY -> opacity = Mth.clamp(opacity + step, MIN_OPACITY, 1);
            }
            changed();
            return true;
        }
        @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
            SoulGui g = new SoulGui(pose);
            int x = this.x, y = this.y;
            if (control == Control.SATURATION) {
                g.fill(x, y, x + width, y + height, 0xFF000000 | Mth.hsvToRgb(hue % 1, 1, 1));
                SoulTheme.horizontalGradient(g, x, y, x + width, y + height, 0xFFFFFFFF, 0x00FFFFFF);
                g.fillGradient(x, y, x + width, y + height, 0, 0xFF000000);
                int px = x + Math.round(saturation * (width - 1)), py = y + Math.round((1 - brightness) * (height - 1));
                g.renderOutline(px - 3, py - 3, 7, 7, SoulTheme.INK);
                g.renderOutline(px - 2, py - 2, 5, 5, SoulTheme.LIGHT);
            } else if (control == Control.HUE) {
                for (int i = 0; i < 6; i++) g.fillGradient(x, y + i * height / 6, x + width, y + (i + 1) * height / 6,
                    0xFF000000 | Mth.hsvToRgb(i / 6f, 1, 1),
                    0xFF000000 | Mth.hsvToRgb(((i + 1) / 6f) % 1, 1, 1));
                int py = y + Math.round(hue * (height - 1));
                g.renderOutline(x - 2, py - 2, width + 4, 5, SoulTheme.LIGHT);
            } else {
                for (int i = 0; i < width; i += 6) for (int row = 0; row < 2; row++)
                    g.fill(x + i, y + row * 6, x + Math.min(i + 6, width), y + (row + 1) * 6,
                        (i / 6 + row) % 2 == 0 ? 0xFF35454C : 0xFF192830);
                SoulTheme.horizontalGradient(g, x, y, x + width, y + height,
                    Math.round(MIN_OPACITY * 255) << 24 | color(), 0xFF000000 | color());
                int px = x + Math.round((opacity - MIN_OPACITY) / (1 - MIN_OPACITY) * (width - 1));
                g.renderOutline(px - 2, y - 2, 5, height + 4, SoulTheme.LIGHT);
            }
            g.renderOutline(x, y, width, height, isFocused() ? SoulTheme.TEAL : SoulTheme.BORDER);
        }
    }

    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        double lx = localX(x), ly = localY(y);
        if (!hex.isMouseOver(lx, ly)) hex.setFocus(false);
        return super.mouseClicked(lx, ly, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(localX(x), localY(y), button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(localX(x), localY(y), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) { return super.mouseScrolled(localX(x), localY(y), delta); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() { preview.clear(); SoulClientConfig.savePreview(); }
    @Override public boolean isPauseScreen() { return true; }
}
