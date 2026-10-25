// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.network.SoulNetwork;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SoulScreen extends Screen {
    private SoulNetwork.State state;
    private UUID selected;
    private int page;
    public SoulScreen(SoulNetwork.State state) {
        super(Component.translatable("screen.soulcatcherplus.title"));
        this.state = state; this.selected = state.selected();
    }
    public void update(SoulNetwork.State state) {
        this.state = state;
        if (selected == null) selected = state.selected();
        rebuildWidgets();
    }
    private Component text(String key) { return Component.translatable("screen.soulcatcherplus." + key); }
    private void send(SoulNetwork.Action action) { SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.Request(action, selected)); }
    private void button(int x, int y, int w, Component label, Runnable action) {
        addRenderableWidget(Button.builder(label, b -> action.run()).bounds(x, y, w, 20).build());
    }
    @Override protected void init() {
        int left = width / 2 - 155, top = Math.max(8, height / 2 - 110);
        page = Math.min(page, Math.max(0, (state.rows().size() - 1) / 4));
        for (int i = page * 4; i < Math.min(state.rows().size(), page * 4 + 4); i++) {
            SoulNetwork.Row row = state.rows().get(i);
            String label = (row.id().equals(selected) ? "> " : "") + row.name()
                + " | " + row.cost() + " / " + row.slots()
                + (row.active() ? " [ON]" : row.cooldown() > 0 ? " [" + row.cooldown() + "s]" : "");
            button(left, top + 38 + (i % 4) * 23, 310, Component.literal(label), () -> {
                selected = row.id(); send(SoulNetwork.Action.SELECT); rebuildWidgets();
            });
        }
        int y = top + 134;
        button(left, y, 70, text("previous"), () -> { if (page > 0) page--; rebuildWidgets(); });
        button(left + 75, y, 70, text("next"), () -> { if ((page + 1) * 4 < state.rows().size()) page++; rebuildWidgets(); });
        button(left + 150, y, 75, text("summon"), () -> send(SoulNetwork.Action.SUMMON));
        button(left + 230, y, 80, text("recall"), () -> send(SoulNetwork.Action.RECALL));
        button(left, y + 24, 100, text("follow"), () -> send(SoulNetwork.Action.FOLLOW));
        button(left + 105, y + 24, 100, text("wait"), () -> send(SoulNetwork.Action.WAIT));
        button(left + 210, y + 24, 100, text("guard"), () -> send(SoulNetwork.Action.GUARD));
        button(left, y + 48, 150, text(state.commandMode() ? "command" : "harvest"), () -> send(SoulNetwork.Action.TOGGLE_MODE));
        button(left + 155, y + 48, 155, text(state.group() ? "group" : "single"), () -> send(SoulNetwork.Action.TOGGLE_GROUP));
        button(left, y + 72, 150, text("recall_all"), () -> send(SoulNetwork.Action.RECALL_ALL));
        button(left + 155, y + 72, 155, text("close"), this::onClose);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int top = Math.max(8, height / 2 - 110);
        graphics.drawCenteredString(font, title, width / 2, top, 0x80ffee);
        graphics.drawCenteredString(font, Component.translatable("screen.soulcatcherplus.balance",
            state.energy(), state.slots(), dev.soulcatcherplus.companion.SoulService.CAPACITY), width / 2, top + 16, 0xffffff);
        if (state.rows().isEmpty()) graphics.drawCenteredString(font, text("empty"), width / 2, top + 75, 0xaaaaaa);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
