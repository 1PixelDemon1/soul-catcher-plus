// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.client.SoulGui;

/** Code-drawn ink, aged brass and soul-light surfaces; no vanilla button atlas. */
final class SoulTheme {
    static final int INK = 0xFF0B131C, PANEL = 0xFF101E28, BORDER = 0xFF2B454B;
    static final int LIGHT = 0xFFE4F0E9, MUTED = 0xFF89A5A4, TEAL = 0xFF7BE2C9;
    static final int GOLD = 0xFFC6AA76, RED = 0xFFD58F82;
    static final int BOSS_BLUE = 0xFF70BFFF;
    private static final String[] STAR = {
        "....#....", "...###...", "...###...", "#########", ".#######.",
        "..#####..", "..#####..", ".###.###.", ".##...##."
    };

    static void panel(SoulGui g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL, INK);
        g.renderOutline(x, y, w, h, BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF20373E);
        for (int side : new int[]{0, 1}) {
            int cx = side == 0 ? x : x + w - 1;
            int direction = side == 0 ? 1 : -1;
            g.fill(Math.min(cx, cx + direction * 8), y, Math.max(cx, cx + direction * 8) + 1, y + 1, GOLD);
            g.fill(cx, y, cx + 1, y + 7, GOLD);
        }
    }
    static void diamond(SoulGui g, int x, int y, int radius, int color) {
        for (int offset = -radius; offset <= radius; offset++) {
            int half = radius - Math.abs(offset);
            g.fill(x - half, y + offset, x + half + 1, y + offset + 1, color);
        }
    }
    static void star(SoulGui g, int x, int y, int color, boolean filled) {
        int left = x - STAR[0].length() / 2, top = y - STAR.length / 2;
        for (int row = 0; row < STAR.length; row++) for (int column = 0; column < STAR[row].length(); column++) {
            if (!starPixel(column, row)) continue;
            if (filled || !starPixel(column - 1, row) || !starPixel(column + 1, row)
                    || !starPixel(column, row - 1) || !starPixel(column, row + 1))
                g.fill(left + column, top + row, left + column + 1, top + row + 1, color);
        }
    }
    private static boolean starPixel(int x, int y) {
        return y >= 0 && y < STAR.length && x >= 0 && x < STAR[y].length() && STAR[y].charAt(x) == '#';
    }
    static void line(SoulGui g, float x1, float y1, float x2, float y2, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(x1 + (x2 - x1) * i / steps);
            int y = Math.round(y1 + (y2 - y1) * i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
    static void ellipse(SoulGui g, int x, int y, int rx, int ry, int color) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            line(g, x + (float) Math.cos(a) * rx, y + (float) Math.sin(a) * ry,
                x + (float) Math.cos(b) * rx, y + (float) Math.sin(b) * ry, color);
        }
    }
    private SoulTheme() {}
}
