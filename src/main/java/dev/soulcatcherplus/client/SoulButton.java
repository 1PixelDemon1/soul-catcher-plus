// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

final class SoulButton extends AbstractButton {
    enum Style { PRIMARY, SUBTLE, DANGER, CARD, FAVORITE }
    private final Runnable action;
    private final Style style;
    private Component subtitle = Component.literal("");
    private Component hint = Component.literal("");
    private boolean selected;
    private boolean lit;
    private boolean boss;

    SoulButton(int x, int y, int width, int height, Component title, Style style, Runnable action) {
        super(x, y, width, height, title);
        this.style = style;
        this.action = action;
    }
    SoulButton subtitle(Component value) { subtitle = value; return this; }
    SoulButton hint(Component value) { hint = value; return this; }
    SoulButton selected(boolean value) { selected = value; return this; }
    SoulButton lit(boolean value) { lit = value; return this; }
    SoulButton boss(boolean value) { boss = value; return this; }
    Component hint() { return hint; }
    @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narration) { defaultButtonNarrationText(narration); }
    @Override public void onPress() { action.run(); }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean hover = isHovered() || isFocused();
        int accent = style == Style.FAVORITE ? SoulTheme.GOLD : style == Style.DANGER ? SoulTheme.RED
            : boss ? SoulTheme.BOSS_PURPLE : SoulTheme.TEAL;
        int border = active && (hover || selected || boss) ? accent : SoulTheme.BORDER;
        int top = style == Style.PRIMARY ? 0xFF214A4B : 0xFF172C35;
        if (selected) top = 0xFF203E43;
        if (hover && active) top = style == Style.DANGER ? 0xFF3F2D33 : 0xFF29484B;
        if (boss) top = selected || (hover && active) ? 0xFF3C2B50 : 0xFF281E38;
        g.fillGradient(getX(), getY(), getX() + width, getY() + height, top, active ? 0xFF101E28 : 0xFF0D1820);
        g.renderOutline(getX(), getY(), width, height, border);
        if (selected || style == Style.PRIMARY) g.fill(getX(), getY() + 1, getX() + 2, getY() + height - 1, accent);
        int color = active ? (hover || selected ? SoulTheme.LIGHT : 0xFFBBD0CC) : 0xFF657C7E;
        if (style == Style.CARD) {
            String title = fit(getMessage().getString(), width - 25);
            g.drawString(font, title, getX() + 9, getY() + 7, boss && active ? SoulTheme.BOSS_PURPLE : color, false);
            g.drawString(font, fit(subtitle.getString(), width - 18), getX() + 9, getY() + 23, SoulTheme.MUTED, false);
            if (lit) SoulTheme.diamond(g, getX() + width - 10, getY() + 10, 2, accent);
        } else if (style == Style.FAVORITE) {
            SoulTheme.star(g, getX() + width / 2, getY() + height / 2,
                selected ? SoulTheme.GOLD : hover ? SoulTheme.LIGHT : SoulTheme.MUTED, selected);
        } else {
            g.drawCenteredString(font, Component.literal(fit(getMessage().getString(), width - 10)),
                getX() + width / 2, getY() + (height - 8) / 2, color);
        }
        if (isFocused()) g.renderOutline(getX() + 2, getY() + 2, width - 4, height - 4, 0x6683DBC8);
    }
    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
