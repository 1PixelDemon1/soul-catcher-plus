// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/** Hover an entry or the whole group, then an outer command; release the trigger to commit. */
public final class SoulWheelScreen extends Screen {
    private static final int W = 460, H = 430, CX = 230, CY = 205, PAGE_SIZE = 5;
    private static final int COMPANION_INNER = 64, COMPANION_OUTER = 116, COMMAND_INNER = 124, COMMAND_OUTER = 178;
    private static final int CENTER_RADIUS = 58;
    private static final double TAU = Math.PI * 2;
    private static final List<SoulNetwork.WheelAction> COMMANDS = com.google.common.collect.ImmutableList.of(SoulNetwork.WheelAction.FOLLOW,
        SoulNetwork.WheelAction.AGGRESSIVE, SoulNetwork.WheelAction.WAIT,
        SoulNetwork.WheelAction.GUARD, SoulNetwork.WheelAction.TELEPORT, SoulNetwork.WheelAction.RECALL);
    private final Level openedLevel = Minecraft.getInstance().level;
    private SoulNetwork.WheelState state;
    private UUID selected;
    private SoulNetwork.WheelAction armed;
    private boolean closed, allSelected;
    private double pointerX = CX, pointerY = CY;
    private int page, ticks;
    private float scale, originX, originY;

    public SoulWheelScreen() { super(text("title")); }
    private static Component text(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable("wheel.soulcatcherplus." + key, args);
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
        if (selected != null) {
            for (int i = 0; i < state.companions().size(); i++) {
                if (state.companions().get(i).entry().equals(selected)) {
                    page = i / PAGE_SIZE; // Keep the locked recipient visible when the roster changes.
                    return;
                }
            }
            // Losing a recipient must not silently lock a different soul under the pointer.
            armed = null;
        }
        if (state.companions().isEmpty()) armed = null;
    }
    private List<SoulNetwork.Companion> visible() {
        if (state == null) return com.google.common.collect.ImmutableList.of();
        int from = page * PAGE_SIZE;
        return state.companions().subList(from, Math.min(from + PAGE_SIZE, state.companions().size()));
    }
    private int lastPage() { return state == null ? 0 : Math.max(0, (state.companions().size() - 1) / PAGE_SIZE); }
    private SoulNetwork.Companion selected() {
        return state == null ? null : state.companions().stream().filter(c -> c.entry().equals(selected)).findFirst().orElse(null);
    }
    private void send(SoulNetwork.WheelAction action) {
        SoulNetwork.sendToServer(new SoulNetwork.WheelRequest(action, selected, allSelected));
    }
    @Override public void tick() {
        if (!ClientControls.canUseMenus(minecraft) || minecraft.level != openedLevel) {
            onClose(); return;
        }
        if (!ClientControls.wheelHeld()) { commitAndClose(); return; }
        if (++ticks % 5 == 0) send(SoulNetwork.WheelAction.REFRESH);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();
        if (ClientControls.COMMAND_WHEEL.matches(event)) return true;
        return super.keyPressed(event);
    }
    @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        int keyCode=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();
        if (ClientControls.COMMAND_WHEEL.matches(event)) {
            ClientControls.wheelTriggerReleased();
            commitAndClose(); return true;
        }
        return super.keyReleased(event);
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x=event.x(), y=event.y(); int button=event.button();
        if (ClientControls.COMMAND_WHEEL.matchesMouse(event)) {
            ClientControls.wheelTriggerReleased();
            commitAndClose(); return true;
        }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(x, y,event.buttonInfo()));
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double x=event.x(), y=event.y(); int button=event.button();
        updatePointer(x, y);
        return true;
    }
    @Override public void mouseMoved(double x, double y) { updatePointer(x, y); }
    private boolean hasSelection() { return selected != null || allSelected; }
    private void clearSelection() {
        selected = null;
        allSelected = false;
        armed = null;
    }
    private void updatePointer(double x, double y) {
        pointerX = (x - originX) / scale;
        pointerY = (y - originY) / scale;
        double dx = pointerX - CX, dy = pointerY - CY;
        if (dx * dx + dy * dy <= CENTER_RADIUS * CENTER_RADIUS) {
            clearSelection();
        } else if (!hasSelection()) {
            List<SoulNetwork.Companion> companions = visible();
            int companion = hit(pointerX, pointerY, COMPANION_INNER, COMPANION_OUTER, choiceCount(companions));
            if (companion >= 0) {
                allSelected = companion == 0;
                selected = allSelected ? null : companions.get(companion - 1).entry();
            }
        }
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
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (delta == 0) return false;
        updatePointer(x, y);
        // Paging must not silently replace a locked recipient; return to the center first.
        if (hasSelection()) return true;
        int next = Mth.clamp(page + (delta < 0 ? 1 : -1), 0, lastPage());
        if (next != page) {
            page = next;
            clearSelection();
        }
        return true;
    }
    private boolean enabled(SoulNetwork.WheelAction action) {
        if (allSelected) return state != null && !state.companions().isEmpty()
            && (action == SoulNetwork.WheelAction.RECALL || state.companions().stream().anyMatch(SoulNetwork.Companion::loaded));
        SoulNetwork.Companion companion = selected();
        return companion != null && (companion.loaded() || action == SoulNetwork.WheelAction.RECALL);
    }
    private static int choiceCount(List<SoulNetwork.Companion> companions) {
        return companions.isEmpty() ? 0 : companions.size() + 1;
    }
    private static int hit(double x, double y, int inner, int outer, int count) {
        if (count == 0) return -1;
        double dx = x - CX, dy = y - CY, radius = Math.sqrt(dx * dx + dy * dy);
        if (radius < inner || radius > outer) return -1;
        double step = TAU / count;
        double angle = (Math.atan2(dy, dx) + Math.PI / 2 + step / 2 + TAU) % TAU;
        return Math.min(count - 1, (int)(angle / step));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x95030910);
        g.pose().pushMatrix();
        g.pose().translate((float)(originX), (float)(originY));
        g.pose().scale(scale, scale);
        updatePointer(mouseX, mouseY);
        double x = pointerX, y = pointerY;
        List<SoulNetwork.Companion> companions = visible();
        int choices = choiceCount(companions);
        int hoveredCompanion = hasSelection() ? -1 : hit(x, y, COMPANION_INNER, COMPANION_OUTER, choices);
        int hoveredCommand = hit(x, y, COMMAND_INNER, COMMAND_OUTER, COMMANDS.size());
        g.centeredText(font, title, CX, 10, SoulTheme.GOLD);
        sector(g, 0, CENTER_RADIUS, 0, TAU, SoulTheme.INK);
        SoulTheme.ellipse(g, CX, CY, CENTER_RADIUS, CENTER_RADIUS, SoulTheme.GOLD);
        g.centeredText(font, state == null ? net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.energy")
            : SoulCages.location(state.cageSlot()), CX, CY - 21, SoulTheme.MUTED);
        g.centeredText(font, state == null ? "…" : state.cageCapacity() == 0 ? "—"
            : state.energy() + " / " + state.cageCapacity(), CX, CY - 3, SoulTheme.TEAL);
        g.centeredText(font, text("active", state == null ? "—" : state.companions().size()), CX, CY + 17, SoulTheme.MUTED);
        if (companions.isEmpty()) {
            sector(g, COMPANION_INNER, COMPANION_OUTER, 0, TAU, SoulTheme.PANEL);
        } else {
            double step = TAU / choices;
            for (int i = 0; i < choices; i++) {
                boolean chosen = i == 0 ? allSelected : companions.get(i - 1).entry().equals(selected);
                int baseColor = i == 0 ? 0xFF2B2923 : SoulTheme.PANEL;
                int hoverColor = i == 0 ? 0xFF403A2B : 0xFF25434C;
                int selectedColor = i == 0 ? 0xFF55492F : 0xFF285A58;
                double center = -Math.PI / 2 + step * i;
                sector(g, COMPANION_INNER, COMPANION_OUTER, center - step / 2 + 0.02, center + step / 2 - 0.02,
                    chosen ? selectedColor : i == hoveredCompanion ? hoverColor : baseColor);
            }
        }
        double commandStep = TAU / COMMANDS.size();
        for (int i = 0; i < COMMANDS.size(); i++) {
            double center = -Math.PI / 2 + commandStep * i;
            boolean danger = COMMANDS.get(i) == SoulNetwork.WheelAction.RECALL;
            sector(g, COMMAND_INNER, COMMAND_OUTER, center - commandStep / 2 + 0.025, center + commandStep / 2 - 0.025,
                !enabled(COMMANDS.get(i)) ? 0xE0101922 : i == hoveredCommand
                    ? danger ? 0xFF51383D : 0xFF315352 : 0xF0182B36);
        }
        // Draw labels after every sector so queued GUI text cannot be covered by a later ring.
        for (int i = 0; i < choices; i++) {
            double angle = -Math.PI / 2 + TAU * i / choices;
            int tx = CX + (int)(Math.cos(angle) * 90), ty = CY + (int)(Math.sin(angle) * 90);
            if (i == 0) {
                g.centeredText(font, text("all"), tx, ty - 12, SoulTheme.GOLD);
                g.centeredText(font, text("group_count", state.companions().size()), tx, ty + 1, SoulTheme.TEAL);
                continue;
            }
            SoulNetwork.Companion companion = companions.get(i - 1);
            g.centeredText(font, fit(companion.name(), 96), tx, ty - 12, SoulTheme.LIGHT);
            Component health = companion.loaded() ? text("health", hp(companion.health()), hp(companion.maxHealth())) : text("unloaded");
            g.centeredText(font, health, tx, ty + 1, companion.loaded() ? SoulTheme.TEAL : SoulTheme.MUTED);
            if (companion.loaded()) g.centeredText(font, commandName(companion.command()), tx, ty + 13, SoulTheme.GOLD);
        }
        for (int i = 0; i < COMMANDS.size(); i++) {
            double angle = -Math.PI / 2 + commandStep * i;
            Component label = allSelected && COMMANDS.get(i) == SoulNetwork.WheelAction.RECALL
                ? net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.recall_all") : commandName(COMMANDS.get(i).name());
            g.centeredText(font, label, CX + (int)(Math.cos(angle) * 151),
                CY + (int)(Math.sin(angle) * 151) - 4, !enabled(COMMANDS.get(i)) ? SoulTheme.MUTED
                    : COMMANDS.get(i) == SoulNetwork.WheelAction.RECALL ? SoulTheme.RED : SoulTheme.LIGHT);
        }
        if (companions.isEmpty()) g.centeredText(font, text(state == null ? "loading" : "empty"), CX, CY - 92, SoulTheme.MUTED);
        Component hint = allSelected ? text("group_hint", ClientControls.COMMAND_WHEEL.getTranslatedKeyMessage())
            : selected != null && selected() == null ? text("selection_lost")
            : selected() != null && !selected().loaded() ? text("unloaded_hint")
            : text("hint", ClientControls.COMMAND_WHEEL.getTranslatedKeyMessage());
        if (lastPage() > 0) g.centeredText(font, text("page", page + 1, lastPage() + 1), CX, 386, SoulTheme.GOLD);
        g.textWithWordWrap(font, hint, 30, 401, W - 60, SoulTheme.MUTED);
        g.pose().popMatrix();
    }
    private static String hp(float value) {
        return value == (int)value ? Integer.toString((int)value) : String.format(Locale.ROOT, "%.1f", value);
    }
    private static Component commandName(String command) {
        return switch (command) {
            case "FOLLOW" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.follow_short");
            case "AGGRESSIVE" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.aggressive_short");
            case "WAIT" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.wait");
            case "GUARD" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.guard_short");
            case "RECALL" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.recall");
            case "TELEPORT" -> net.minecraft.network.chat.Component.translatable("screen.soulcatcherplus.teleport_short");
            default -> net.minecraft.network.chat.Component.literal("");
        };
    }
    private String fit(String value, int maxWidth) {
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, maxWidth - font.width("…")) + "…";
    }
    private record SectorKey(int inner, int outer, double start, double end) {}
    private record Span(int x, int y, int width) {}
    private static final java.util.Map<SectorKey,java.util.List<Span>> SECTORS = new java.util.HashMap<>();
    private static void sector(GuiGraphicsExtractor g, int inner, int outer, double start, double end, int color) {
        var spans=SECTORS.computeIfAbsent(new SectorKey(inner,outer,start,end), key -> {
            var result=new java.util.ArrayList<Span>();
            for(int y=-outer;y<=outer;y++) {
                int run=Integer.MIN_VALUE;
                for(int x=-outer;x<=outer+1;x++) {
                    double distance=x*x+y*y, angle=Math.atan2(y,x);
                    while(angle<start) angle+=Math.PI*2;
                    boolean inside=x<=outer && distance>=inner*inner && distance<=outer*outer && angle<end;
                    if(inside && run==Integer.MIN_VALUE) run=x;
                    else if(!inside && run!=Integer.MIN_VALUE) { result.add(new Span(run,y,x-run));run=Integer.MIN_VALUE; }
                }
            }
            return java.util.List.copyOf(result);
        });
        for(var span:spans) g.fill(CX+span.x(),CY+span.y(),CX+span.x()+span.width(),CY+span.y()+1,color);
    }
    @Override public void onClose() { closed = true; super.onClose(); }
    @Override public void removed() { closed = true; ClientControls.wheelClosed(); }
    @Override public boolean isPauseScreen() { return false; }
}
