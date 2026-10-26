// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Hover an entry, then an outer command; release the trigger to address only that entry. */
public final class SoulWheelScreen extends Screen {
    private static final int W = 460, H = 430, CX = 230, CY = 205, PAGE_SIZE = 6;
    private static final int COMPANION_INNER = 64, COMPANION_OUTER = 116, COMMAND_INNER = 124, COMMAND_OUTER = 178;
    private static final double TAU = Math.PI * 2;
    private static final List<SoulNetwork.WheelAction> COMMANDS = List.of(SoulNetwork.WheelAction.FOLLOW,
        SoulNetwork.WheelAction.WAIT, SoulNetwork.WheelAction.GUARD, SoulNetwork.WheelAction.RECALL);
    private final Level openedLevel = Minecraft.getInstance().level;
    private SoulNetwork.WheelState state;
    private UUID selected;
    private SoulNetwork.WheelAction armed;
    private boolean closed;
    private double pointerX = CX, pointerY = CY;
    private int page, ticks;
    private float scale, originX, originY;

    public SoulWheelScreen() { super(text("title")); }
    private static Component text(String key, Object... args) {
        return Component.translatable("wheel.soulcatcherplus." + key, args);
    }
    @Override protected void init() {
        scale = Math.max(0.25f, Math.min(1.5f, Math.min((width - 12f) / W, (height - 12f) / H)));
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        send(SoulNetwork.WheelAction.OPEN);
    }
    public void update(SoulNetwork.WheelState state) {
        this.state = state;
        page = Math.min(page, lastPage());
        if (state.companions().stream().noneMatch(c -> c.entry().equals(selected))) {
            selected = null;
            armed = null;
        }
    }
    private List<SoulNetwork.Companion> visible() {
        if (state == null) return List.of();
        int from = page * PAGE_SIZE;
        return state.companions().subList(from, Math.min(from + PAGE_SIZE, state.companions().size()));
    }
    private int lastPage() { return state == null ? 0 : Math.max(0, (state.companions().size() - 1) / PAGE_SIZE); }
    private SoulNetwork.Companion selected() {
        return state == null ? null : state.companions().stream().filter(c -> c.entry().equals(selected)).findFirst().orElse(null);
    }
    private void send(SoulNetwork.WheelAction action) {
        SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.WheelRequest(action, selected));
    }
    @Override public void tick() {
        if (!ClientControls.canUseMenus(minecraft) || minecraft.level != openedLevel) {
            onClose(); return;
        }
        if (!ClientControls.wheelHeld()) { commitAndClose(); return; }
        if (++ticks % 5 == 0) send(SoulNetwork.WheelAction.REFRESH);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientControls.COMMAND_WHEEL.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ClientControls.COMMAND_WHEEL.matches(keyCode, scanCode)) {
            ClientControls.wheelTriggerReleased();
            commitAndClose(); return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (ClientControls.COMMAND_WHEEL.matchesMouse(button)) {
            ClientControls.wheelTriggerReleased();
            commitAndClose(); return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        updatePointer(x, y);
        return true;
    }
    @Override public void mouseMoved(double x, double y) { updatePointer(x, y); }
    private void updatePointer(double x, double y) {
        pointerX = (x - originX) / scale;
        pointerY = (y - originY) / scale;
        List<SoulNetwork.Companion> companions = visible();
        int companion = hit(pointerX, pointerY, COMPANION_INNER, COMPANION_OUTER, companions.size());
        if (companion >= 0) selected = companions.get(companion).entry();
        int command = hit(pointerX, pointerY, COMMAND_INNER, COMMAND_OUTER, COMMANDS.size());
        armed = command >= 0 && enabled(COMMANDS.get(command)) ? COMMANDS.get(command) : null;
    }
    private void commitAndClose() {
        if (closed) return;
        closed = true;
        if (ClientControls.canUseMenus(minecraft) && minecraft.level == openedLevel) {
            // Input can move and release between rendered frames; never execute a stale hovered command.
            updatePointer(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth(),
                minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight());
            if (armed != null && enabled(armed)) send(armed);
        }
        onClose();
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        int next = Mth.clamp(page + (delta < 0 ? 1 : -1), 0, lastPage());
        if (next != page) {
            page = next;
            selected = null;
            armed = null;
        }
        return true;
    }
    private boolean enabled(SoulNetwork.WheelAction action) {
        SoulNetwork.Companion companion = selected();
        return companion != null && (companion.loaded() || action == SoulNetwork.WheelAction.RECALL);
    }
    private static int hit(double x, double y, int inner, int outer, int count) {
        if (count == 0) return -1;
        double dx = x - CX, dy = y - CY, radius = Math.sqrt(dx * dx + dy * dy);
        if (radius < inner || radius > outer) return -1;
        double step = TAU / count;
        double angle = (Math.atan2(dy, dx) + Math.PI / 2 + step / 2 + TAU) % TAU;
        return Math.min(count - 1, (int)(angle / step));
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x95030910);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        updatePointer(mouseX, mouseY);
        double x = pointerX, y = pointerY;
        List<SoulNetwork.Companion> companions = visible();
        int hoveredCompanion = hit(x, y, COMPANION_INNER, COMPANION_OUTER, companions.size());
        int hoveredCommand = hit(x, y, COMMAND_INNER, COMMAND_OUTER, COMMANDS.size());
        g.drawCenteredString(font, title, CX, 10, SoulTheme.GOLD);
        sector(g, 0, 58, 0, TAU, SoulTheme.INK);
        SoulTheme.ellipse(g, CX, CY, 58, 58, SoulTheme.GOLD);
        g.drawCenteredString(font, Component.translatable("screen.soulcatcherplus.energy"), CX, CY - 21, SoulTheme.MUTED);
        g.drawCenteredString(font, state == null ? "…" : Integer.toString(state.energy()), CX, CY - 3, SoulTheme.TEAL);
        g.drawCenteredString(font, text("active", state == null ? "—" : state.companions().size()), CX, CY + 17, SoulTheme.MUTED);
        if (companions.isEmpty()) {
            sector(g, COMPANION_INNER, COMPANION_OUTER, 0, TAU, SoulTheme.PANEL);
        } else {
            double step = TAU / companions.size();
            for (int i = 0; i < companions.size(); i++) {
                SoulNetwork.Companion companion = companions.get(i);
                boolean chosen = companion.entry().equals(selected);
                double center = -Math.PI / 2 + step * i;
                sector(g, COMPANION_INNER, COMPANION_OUTER, center - step / 2 + 0.02, center + step / 2 - 0.02,
                    chosen ? 0xFF285A58 : i == hoveredCompanion ? 0xFF25434C : SoulTheme.PANEL);
            }
        }
        for (int i = 0; i < COMMANDS.size(); i++) {
            double center = -Math.PI / 2 + Math.PI / 2 * i;
            boolean danger = COMMANDS.get(i) == SoulNetwork.WheelAction.RECALL;
            sector(g, COMMAND_INNER, COMMAND_OUTER, center - Math.PI / 4 + 0.025, center + Math.PI / 4 - 0.025,
                !enabled(COMMANDS.get(i)) ? 0xE0101922 : i == hoveredCommand
                    ? danger ? 0xFF51383D : 0xFF315352 : 0xF0182B36);
        }
        // Draw labels after every sector so queued GUI text cannot be covered by a later ring.
        for (int i = 0; i < companions.size(); i++) {
            SoulNetwork.Companion companion = companions.get(i);
            double angle = -Math.PI / 2 + TAU * i / companions.size();
            int tx = CX + (int)(Math.cos(angle) * 90), ty = CY + (int)(Math.sin(angle) * 90);
            g.drawCenteredString(font, fit(companion.name(), 96), tx, ty - 12, SoulTheme.LIGHT);
            Component health = companion.loaded() ? text("health", hp(companion.health()), hp(companion.maxHealth())) : text("unloaded");
            g.drawCenteredString(font, health, tx, ty + 1, companion.loaded() ? SoulTheme.TEAL : SoulTheme.MUTED);
            if (companion.loaded()) g.drawCenteredString(font, commandName(companion.command()), tx, ty + 13, SoulTheme.GOLD);
        }
        for (int i = 0; i < COMMANDS.size(); i++) {
            double angle = -Math.PI / 2 + Math.PI / 2 * i;
            g.drawCenteredString(font, commandName(COMMANDS.get(i).name()), CX + (int)(Math.cos(angle) * 151),
                CY + (int)(Math.sin(angle) * 151) - 4, !enabled(COMMANDS.get(i)) ? SoulTheme.MUTED
                    : COMMANDS.get(i) == SoulNetwork.WheelAction.RECALL ? SoulTheme.RED : SoulTheme.LIGHT);
        }
        if (companions.isEmpty()) g.drawCenteredString(font, text(state == null ? "loading" : "empty"), CX, CY - 92, SoulTheme.MUTED);
        Component hint = selected() != null && !selected().loaded() ? text("unloaded_hint")
            : text("hint", ClientControls.COMMAND_WHEEL.getTranslatedKeyMessage());
        if (lastPage() > 0) g.drawCenteredString(font, text("page", page + 1, lastPage() + 1), CX, 386, SoulTheme.GOLD);
        g.drawWordWrap(font, hint, 30, 401, W - 60, SoulTheme.MUTED);
        g.pose().popPose();
    }
    private static String hp(float value) {
        return value == (int)value ? Integer.toString((int)value) : String.format(Locale.ROOT, "%.1f", value);
    }
    private static Component commandName(String command) {
        return switch (command) {
            case "FOLLOW" -> Component.translatable("screen.soulcatcherplus.follow_short");
            case "WAIT" -> Component.translatable("screen.soulcatcherplus.wait");
            case "GUARD" -> Component.translatable("screen.soulcatcherplus.guard_short");
            case "RECALL" -> Component.translatable("screen.soulcatcherplus.recall");
            default -> Component.empty();
        };
    }
    private String fit(String value, int maxWidth) {
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, maxWidth - font.width("…")) + "…";
    }
    private static void sector(GuiGraphics g, int inner, int outer, double start, double end, int color) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = Math.max(1, (int)Math.ceil((end - start) * outer / 4));
        int r = color >> 16 & 255, green = color >> 8 & 255, b = color & 255, a = color >>> 24;
        var matrix = g.pose().last().pose();
        for (int i = 0; i < steps; i++) {
            double p = start + (end - start) * i / steps, q = start + (end - start) * (i + 1) / steps;
            buffer.vertex(matrix, CX + (float)Math.cos(p) * inner, CY + (float)Math.sin(p) * inner, 0).color(r, green, b, a).endVertex();
            buffer.vertex(matrix, CX + (float)Math.cos(p) * outer, CY + (float)Math.sin(p) * outer, 0).color(r, green, b, a).endVertex();
            buffer.vertex(matrix, CX + (float)Math.cos(q) * outer, CY + (float)Math.sin(q) * outer, 0).color(r, green, b, a).endVertex();
            buffer.vertex(matrix, CX + (float)Math.cos(q) * inner, CY + (float)Math.sin(q) * inner, 0).color(r, green, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
    @Override public void onClose() { closed = true; super.onClose(); }
    @Override public void removed() { closed = true; ClientControls.wheelClosed(); }
    @Override public boolean isPauseScreen() { return false; }
}
