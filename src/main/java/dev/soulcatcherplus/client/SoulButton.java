// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.Minecraft;
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
    @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { defaultButtonNarrationText(output); }
    @Override public void onPress() { action.run(); }
    @Override public void renderButton(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
        SoulGui g = new SoulGui(pose);
        var font = Minecraft.getInstance().font;
        boolean hover = isHoveredOrFocused();
        int accent = style == Style.FAVORITE ? SoulTheme.GOLD : style == Style.DANGER ? SoulTheme.RED
            : boss ? SoulTheme.BOSS_PURPLE : SoulTheme.TEAL;
        int border = active && (hover || selected || boss) ? accent : SoulTheme.BORDER;
        int top = style == Style.PRIMARY ? 0xFF214A4B : 0xFF172C35;
        if (selected) top = 0xFF203E43;
        if (hover && active) top = style == Style.DANGER ? 0xFF3F2D33 : 0xFF29484B;
        if (boss) top = selected || (hover && active) ? 0xFF3C2B50 : 0xFF281E38;
        g.fillGradient(x, y, x + width, y + height, top, active ? 0xFF101E28 : 0xFF0D1820);
        g.renderOutline(x, y, width, height, border);
        if (selected || style == Style.PRIMARY) g.fill(x, y + 1, x + 2, y + height - 1, accent);
        int color = active ? (hover || selected ? SoulTheme.LIGHT : 0xFFBBD0CC) : 0xFF657C7E;
        if (style == Style.CARD) {
            String title = fit(getMessage().getString(), width - 25);
            g.drawString(font, title, x + 9, y + 7, boss && active ? SoulTheme.BOSS_PURPLE : color, false);
            g.drawString(font, fit(subtitle.getString(), width - 18), x + 9, y + 23, SoulTheme.MUTED, false);
            if (lit) SoulTheme.diamond(g, x + width - 10, y + 10, 2, accent);
        } else if (style == Style.FAVORITE) {
            SoulTheme.star(g, x + width / 2, y + height / 2,
                selected ? SoulTheme.GOLD : hover ? SoulTheme.LIGHT : SoulTheme.MUTED, selected);
        } else {
            g.drawCenteredString(font, Component.literal(fit(getMessage().getString(), width - 10)),
                x + width / 2, y + (height - 8) / 2, color);
        }
        if (isFocused()) g.renderOutline(x + 2, y + 2, width - 4, height - 4, 0x6683DBC8);
    }
    private static String fit(String text, int width) {
        var font = Minecraft.getInstance().font;
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }
}
