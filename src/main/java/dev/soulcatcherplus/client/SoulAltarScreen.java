// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.soulcatcherplus.altar.SoulAltarMenu;
import dev.soulcatcherplus.data.SoulBalance;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;

public final class SoulAltarScreen extends AbstractContainerScreen<SoulAltarMenu> {
    private static final int LIST_X = 15, LIST_Y = 52, LIST_WIDTH = 138, ROW_HEIGHT = 13, VISIBLE_ROWS = 6;
    private static final int FRAME = 0xFF3A555B, BACK = 0xFF111E26, MUTE = 0xFF9DB3B7, TEXT = 0xFFDEEDEB, CYAN = 0xFF75DFE4;
    private List<SoulNetwork.Row> rows = new ArrayList<>();
    private final List<SoulNetwork.Row> filtered = new ArrayList<>();
    private final HashMap<java.util.UUID, Integer> forms = new HashMap<>();
    private SoulNetwork.Row selected;
    private EditBox search;
    private Button inscribe;
    private String query = "";
    private int offset;
    private boolean loaded;

    public SoulAltarScreen(SoulAltarMenu menu, Inventory player, Component title) {
        super(menu, player, title, 312, 232);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.soulcatcherplus.altar_" + key, args);
    }

    @Override protected void init() {
        super.init();
        search = addRenderableWidget(new EditBox(font, leftPos + LIST_X, topPos + 30, LIST_WIDTH, 16, text("search")));
        search.setMaxLength(64);
        search.setSuggestion(query.isEmpty() ? text("search").getString() : "");
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            search.setSuggestion(query.isEmpty() ? text("search").getString() : "");
            offset = 0;
            filter();
        });
        inscribe = addRenderableWidget(Button.builder(text("write"), button -> {
            if (ready()) SoulNetwork.sendToServer(new SoulNetwork.AltarInscribe(menu.containerId, selected.id()));
        }).bounds(leftPos + 170, topPos + 110, 126, 18).build());
        inscribe.active = ready();
    }

    public void update(SoulNetwork.State state) {
        rows = state.rows();
        loaded = true;
        var perType = new HashMap<String, Integer>();
        forms.clear();
        for (var row : rows) forms.put(row.id(), perType.merge(row.entityType(), 1, Integer::sum));
        var wanted = selected == null ? state.selected() : selected.id();
        selected = rows.stream().filter(row -> row.id().equals(wanted)).findFirst().orElse(null);
        filter();
    }

    private void filter() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (var row : rows) {
            if (label(row).toLowerCase(Locale.ROOT).contains(needle) || row.entityType().contains(needle)) filtered.add(row);
        }
        offset = Mth.clamp(offset, 0, maxOffset());
    }

    private int maxOffset() { return Math.max(0, filtered.size() - VISIBLE_ROWS); }

    private String label(SoulNetwork.Row row) {
        String name = row.name().isEmpty() ? row.entityType() : row.displayName();
        int form = forms.getOrDefault(row.id(), 1);
        return form == 1 ? name : name + " #" + form;
    }

    private boolean ready() {
        return selected != null && menu.hasPaper() && !menu.resultOccupied() && menu.energy() >= selected.cost() && menu.capacity() > 0;
    }

    private Component status() {
        if (!loaded) return text("loading");
        if (menu.resultOccupied()) return text("take");
        if (selected == null) return text("choose");
        if (menu.capacity() == 0 || !menu.hasPaper()) return text("inputs");
        if (menu.energy() < selected.cost()) return text("insufficient");
        return text("ready");
    }

    @Override protected void containerTick() {
        super.containerTick();

        inscribe.active = ready();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(pose, mouseX, mouseY, partialTick);
        int index = rowAt(mouseX, mouseY);
        if (index >= 0 && menu.getCarried().isEmpty()) {
            var row = filtered.get(index);
            pose.setTooltipForNextFrame(Component.literal(label(row)), mouseX, mouseY);
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        paint(pose, 0, 0, imageWidth, imageHeight, FRAME);
        paint(pose, 1, 1, imageWidth - 1, imageHeight - 1, BACK);
        paint(pose, 15, 24, 297, 25, FRAME);
        paint(pose, 161, 30, 162, 130, FRAME);
        paint(pose, 15, 134, 297, 135, FRAME);
        paint(pose, LIST_X, LIST_Y, LIST_X + LIST_WIDTH, LIST_Y + VISIBLE_ROWS * ROW_HEIGHT, 0xFF0B161D);
        for (int line = 0; line < VISIBLE_ROWS && offset + line < filtered.size(); line++) {
            var row = filtered.get(offset + line);
            int y = LIST_Y + line * ROW_HEIGHT;
            boolean active = selected != null && selected.id().equals(row.id());
            if (active || rowAt(mouseX, mouseY) == offset + line) {
                paint(pose, LIST_X, y, LIST_X + LIST_WIDTH, y + ROW_HEIGHT, active ? 0xFF24454D : 0xFF192F37);
                if (active) paint(pose, LIST_X, y, LIST_X + 2, y + ROW_HEIGHT, CYAN);
            }
        }
        if (maxOffset() > 0) {
            int y = LIST_Y + (int)(offset * 64f / maxOffset());
            paint(pose, 155, LIST_Y, 158, 130, 0xFF22333C);
            paint(pose, 155, y, 158, y + 14, CYAN);
        }
        slot(pose, SoulAltarMenu.CAGE_X, SoulAltarMenu.INPUT_Y);
        slot(pose, SoulAltarMenu.PAPER_X, SoulAltarMenu.INPUT_Y);
        slot(pose, SoulAltarMenu.RESULT_X, SoulAltarMenu.INPUT_Y);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(pose, 75 + col * 18, 151 + row * 18);
        for (int col = 0; col < 9; col++) slot(pose, 75 + col * 18, 209);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor pose, int mouseX, int mouseY) {
        pose.text(font, title, 15, 9, CYAN);
        for (int line = 0; line < VISIBLE_ROWS && offset + line < filtered.size(); line++) {
            var row = filtered.get(offset + line);
            pose.text(font, font.plainSubstrByWidth(label(row), LIST_WIDTH - 10), LIST_X + 4, LIST_Y + line * ROW_HEIGHT + 3,
                SoulBalance.isBossSoul(row.maxHealth()) ? 0xFFCBA2FF : TEXT);
        }
        if (filtered.isEmpty())
            pose.text(font, text(loaded ? (rows.isEmpty() ? "empty" : "no_matches") : "loading"), LIST_X + 4, LIST_Y + 7, MUTE);
        pose.text(font, font.plainSubstrByWidth(selected == null ? text("choose").getString() : label(selected), 125), 170, 31, TEXT);
        center(pose, text("cage"), SoulAltarMenu.CAGE_X + 8, 49);
        center(pose, text("paper"), SoulAltarMenu.PAPER_X + 8, 49);
        center(pose, text("scroll"), SoulAltarMenu.RESULT_X + 8, 49);
        pose.text(font, ">", 250, 65, CYAN);
        pose.text(font, text("cost", selected == null ? "—" : selected.cost()), 170, 82, TEXT);
        pose.text(font, text("energy", menu.energy(), menu.capacity()), 170, 96, CYAN);
        pose.text(font, font.plainSubstrByWidth(status().getString(), 123), 169, 138, MUTE);
        pose.text(font, playerInventoryTitle, 75, 140, MUTE);
    }

    private void center(GuiGraphicsExtractor pose, Component value, int x, int y) {
        pose.centeredText(font, value, x, y, MUTE);
    }
    private void slot(GuiGraphicsExtractor pose, int x, int y) {
        paint(pose, x - 1, y - 1, x + 17, y + 17, FRAME);
        paint(pose, x, y, x + 16, y + 16, 0xFF08141B);
    }
    private void paint(GuiGraphicsExtractor pose, int x0, int y0, int x1, int y1, int color) {
        pose.fill(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, color);
    }
    private boolean overList(double x, double y) {
        return x >= leftPos + LIST_X && x < leftPos + LIST_X + LIST_WIDTH + 6
            && y >= topPos + LIST_Y && y < topPos + LIST_Y + ROW_HEIGHT * VISIBLE_ROWS;
    }
    private int rowAt(double x, double y) {
        if (!overList(x, y) || x >= leftPos + LIST_X + LIST_WIDTH) return -1;
        int index = offset + (int)(y - topPos - LIST_Y) / ROW_HEIGHT;
        return index < filtered.size() ? index : -1;
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        int button = event.button();
        if (event.button() == 0 && rowAt(x, y) >= 0) {
            search.setFocused(false);
            selected = filtered.get(rowAt(x, y));
            inscribe.active = ready();
            return true;
        }
        if (!search.isMouseOver(x, y)) search.setFocused(false);
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (delta != 0 && overList(x, y)) {
            offset = Mth.clamp(offset + (delta > 0 ? -1 : 1), 0, maxOffset());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, delta);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (search.isFocused() && key != InputConstants.KEY_ESCAPE) {
            if (search.keyPressed(event) || minecraft.options.keyInventory.matches(event)) return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        return search.charTyped(event) || super.charTyped(event);
    }
}
