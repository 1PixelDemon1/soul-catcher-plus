// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.data.SummonRules;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Species archive, detached 3D model, captured-form selector and companion orders. */
public final class SoulScreen extends Screen {
    private static final int W = 620, H = 350, PAGE_SIZE = 4;
    private static final int PX = 190, PY = 88, PW = 232, PH = 146;
    private SoulNetwork.State state;
    private final SoulPreview preview = new SoulPreview();
    private final List<SoulButton> controls = new ArrayList<>();
    private final List<CardControls> cards = new ArrayList<>();
    private final Map<String, UUID> rememberedForms = new HashMap<>();
    private List<SoulGroup> groups = List.of();
    private UUID selected;
    private int page, ticks;
    private long receivedGameTime;
    private float scale = 1, originX, originY;
    private boolean turning;
    private SoulButton primary, previousForm, nextForm, follow, wait, guard, aggressive, scope, recallAll;

    private record SoulGroup(String entityType, List<SoulNetwork.Row> forms) {
        SoulNetwork.Row active() { return forms.stream().filter(SoulNetwork.Row::active).findFirst().orElse(null); }
        boolean favorite() { return forms.get(0).favorite(); }
    }
    private record CardControls(String entityType, SoulButton body, SoulButton favorite) {}
    public SoulScreen(SoulNetwork.State state) {
        super(text("title"));
        this.state = state;
        receivedGameTime = worldTime();
        selected = state.selected();
        regroup();
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).equals(group())) page = i / PAGE_SIZE;
    }
    public void update(SoulNetwork.State state) {
        this.state = state;
        receivedGameTime = worldTime();
        regroup();
        // Preserve hover, keyboard focus and dragging during periodic state synchronization.
        updateControls();
    }
    public void receivePreview(SoulNetwork.Preview packet) {
        if (state.rows().stream().anyMatch(r -> r.id().equals(packet.entry()) && r.entityType().equals(packet.entityType())))
            preview.receive(packet);
    }
    private void regroup() {
        List<String> before = groups.stream().map(SoulGroup::entityType).toList();
        Map<String, List<SoulNetwork.Row>> map = new LinkedHashMap<>();
        state.rows().forEach(r -> map.computeIfAbsent(r.entityType(), t -> new ArrayList<>()).add(r));
        groups = map.entrySet().stream().map(e -> new SoulGroup(e.getKey(), List.copyOf(e.getValue())))
            .sorted(Comparator.comparing(SoulGroup::favorite).reversed()).toList();
        if (state.rows().stream().noneMatch(r -> r.id().equals(selected))) selected = state.rows().isEmpty() ? null : state.rows().get(0).id();
        page = Math.min(page, lastPage());
        // New species or favorite ordering change the layout; ordinary refreshes do not.
        if (minecraft != null && !before.equals(groups.stream().map(SoulGroup::entityType).toList())) rebuildWidgets();
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.soulcatcherplus." + key, args);
    }
    private SoulNetwork.Row row() { return state.rows().stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null); }
    private SoulGroup group() {
        return groups.stream().filter(g -> g.forms().stream().anyMatch(r -> r.id().equals(selected))).findFirst().orElse(null);
    }
    private String name(SoulGroup group) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(group.entityType()));
        return type == null ? group.forms().get(0).name() : type.getDescription().getString();
    }
    private int lastPage() { return Math.max(0, (groups.size() - 1) / PAGE_SIZE); }
    private static long worldTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }
    private long cooldown(SoulNetwork.Row row) {
        // Cosmetic screen ticks continue during a pause; recovery must follow world time.
        long elapsed = Math.max(0, worldTime() - receivedGameTime);
        return Math.max(0, row.cooldown() - elapsed / 20);
    }
    private void send(SoulNetwork.Action action) { send(action, selected); }
    private void send(SoulNetwork.Action action, UUID entry) {
        SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.Request(action, entry));
    }
    private void select(SoulNetwork.Row row) {
        selected = row.id();
        rememberedForms.put(row.entityType(), selected);
        preview.resetView();
        preview.request(selected, ticks);
        send(SoulNetwork.Action.SELECT);
        rebuildWidgets();
    }
    private void select(SoulGroup group) {
        SoulGroup current = groups.stream().filter(g -> g.entityType().equals(group.entityType())).findFirst().orElse(group);
        UUID remembered = rememberedForms.get(current.entityType());
        select(current.forms().stream().filter(r -> r.id().equals(remembered)).findFirst()
            .orElse(current.active() == null ? current.forms().get(0) : current.active()));
    }
    private void changeForm(int direction) {
        SoulGroup group = group();
        if (group != null && group.forms().size() > 1)
            select(group.forms().get(Math.floorMod(group.forms().indexOf(row()) + direction, group.forms().size())));
    }
    private SoulButton button(int x, int y, int width, int height, Component label, SoulButton.Style style, Runnable action) {
        SoulButton button = new SoulButton(x, y, width, height, label, style, action);
        controls.add(button);
        return addRenderableWidget(button);
    }

    @Override protected void init() {
        controls.clear();
        cards.clear();
        turning = false;
        scale = Math.max(0.25f, Math.min(1.5f, Math.min((width - 12f) / W, (height - 12f) / H)));
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        button(592, 19, 16, 20, Component.literal("×"), SoulButton.Style.SUBTLE, this::onClose).hint(text("close"));
        for (int i = page * PAGE_SIZE; i < Math.min(groups.size(), (page + 1) * PAGE_SIZE); i++) {
            SoulGroup group = groups.get(i);
            int y = 88 + (i % PAGE_SIZE) * 43;
            SoulButton body = button(26, y, 118, 39, Component.literal(name(group)), SoulButton.Style.CARD,
                () -> select(group)).subtitle(text("forms_short", group.forms().size())).selected(group.equals(group()))
                .lit(group.active() != null).hint(text("choose_soul"));
            Component favoriteHint = text(group.favorite() ? "remove_favorite" : "add_favorite");
            SoulButton favorite = button(146, y, 20, 39, favoriteHint, SoulButton.Style.FAVORITE,
                () -> send(SoulNetwork.Action.TOGGLE_FAVORITE, group.forms().get(0).id()))
                .selected(group.favorite()).hint(favoriteHint);
            cards.add(new CardControls(group.entityType(), body, favorite));
        }
        button(26, 273, 25, 18, Component.literal("‹"), SoulButton.Style.SUBTLE,
            () -> { page--; rebuildWidgets(); }).hint(text("previous")).active = page > 0;
        button(141, 273, 25, 18, Component.literal("›"), SoulButton.Style.SUBTLE,
            () -> { page++; rebuildWidgets(); }).hint(text("next")).active = page < lastPage();
        previousForm = button(198, 245, 24, 22, Component.literal("‹"), SoulButton.Style.SUBTLE,
            () -> changeForm(-1)).hint(text("previous_form"));
        nextForm = button(390, 245, 24, 22, Component.literal("›"), SoulButton.Style.SUBTLE,
            () -> changeForm(1)).hint(text("next_form"));
        primary = button(448, 161, 144, 27, text("summon"), SoulButton.Style.PRIMARY, () -> {
            SoulNetwork.Row active = group() == null ? null : group().active();
            if (active == null) send(SoulNetwork.Action.SUMMON);
            else send(SoulNetwork.Action.RECALL, active.id());
        });
        follow = button(448, 231, 70, 20, text("follow_short"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.FOLLOW)).hint(text("follow_hint"));
        wait = button(522, 231, 70, 20, text("wait"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.WAIT)).hint(text("wait_hint"));
        guard = button(448, 254, 70, 20, text("guard_short"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.GUARD)).hint(text("guard_hint"));
        aggressive = button(522, 254, 70, 20, text("aggressive_short"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.AGGRESSIVE)).hint(text("aggressive_hint"));
        scope = button(448, 279, 144, 16, text(state.group() ? "group" : "single"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.TOGGLE_GROUP)).hint(text("scope_hint"));
        button(18, 311, 156, 24, text(state.commandMode() ? "command" : "harvest"), SoulButton.Style.SUBTLE,
            () -> send(SoulNetwork.Action.TOGGLE_MODE)).hint(text(state.commandMode() ? "command_hint" : "harvest_hint"));
        recallAll = button(438, 311, 164, 24, text("recall_all"), SoulButton.Style.DANGER,
            () -> send(SoulNetwork.Action.RECALL_ALL)).hint(text("recall_all_hint"));
        updateControls();
        preview.request(selected, ticks);
    }
    private Component summonHint() {
        var row = row();
        var group = group();
        if (row == null || group == null) return text("empty_short");
        if (group.active() != null) return text(group.active().id().equals(selected) ? "recall_hint" : "other_form_active");
        return switch (row.status()) {
            case RECOVERING -> text("recovering", cooldown(row));
            case LIMIT_REACHED -> text("capacity_hint");
            case NO_SCYTHE -> text("scythe_needed");
            case LOW_ENERGY -> text("need_energy", Math.max(0, row.cost() - state.energy()));
            default -> text("summon_hint");
        };
    }
    private void updateControls() {
        if (primary == null) return;
        var group = group();
        var row = row();
        var active = group == null ? null : group.active();
        primary.setMessage(text(active == null ? "summon" : "recall"));
        primary.hint(summonHint());
        primary.active = active != null || (row != null && row.status() == SummonRules.Status.READY);
        previousForm.active = nextForm.active = group != null && group.forms().size() > 1;
        boolean hasCommands = state.group() ? state.rows().stream().anyMatch(SoulNetwork.Row::active) : active != null;
        follow.active = wait.active = guard.active = aggressive.active = hasCommands;
        follow.selected(!state.group() && active != null && active.command().equals("FOLLOW"));
        wait.selected(!state.group() && active != null && active.command().equals("WAIT"));
        guard.selected(!state.group() && active != null && active.command().equals("GUARD"));
        aggressive.selected(!state.group() && active != null && active.command().equals("AGGRESSIVE"));
        scope.setMessage(text(state.group() ? "group" : "single"));
        recallAll.active = state.rows().stream().anyMatch(SoulNetwork.Row::active);
        // Keep card indicators and toggle labels current without losing mouse/keyboard focus.
        for (SoulButton button : controls) {
            if (button.getX() == 18 && button.getY() == 311) {
                button.setMessage(text(state.commandMode() ? "command" : "harvest"));
                button.hint(text(state.commandMode() ? "command_hint" : "harvest_hint"));
            }
        }
        for (CardControls card : cards) {
            SoulGroup species = groups.stream().filter(g -> g.entityType().equals(card.entityType())).findFirst().orElse(null);
            if (species == null) continue;
            card.body().selected(species.equals(group)).lit(species.active() != null)
                .subtitle(text("forms_short", species.forms().size()));
            Component favoriteHint = text(species.favorite() ? "remove_favorite" : "add_favorite");
            card.favorite().selected(species.favorite()).hint(favoriteHint).setMessage(favoriteHint);
        }
    }

    @Override public void tick() {
        ticks++;
        preview.tick();
        preview.request(selected, ticks);
        if (ticks % 20 == 0) send(SoulNetwork.Action.REFRESH);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xEB030810, 0xF2071019);
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1);
        SoulTheme.panel(g, 0, 0, W, H);
        g.fill(1, 1, W - 1, 53, 0xFF111F2A);
        g.fill(18, 52, 602, 53, SoulTheme.BORDER);
        SoulTheme.diamond(g, 31, 29, 9, 0xFF254B4C);
        SoulTheme.diamond(g, 31, 29, 4, SoulTheme.TEAL);
        g.drawString(font, title, 50, 19, SoulTheme.LIGHT, false);
        g.drawString(font, text("subtitle"), 50, 34, SoulTheme.MUTED, false);
        metric(g, 333, 119, text("energy"), Integer.toString(state.energy()), SoulTheme.TEAL);
        metric(g, 460, 125, text("capacity_label"), state.activeCount() + " / " + state.summonLimit(), SoulTheme.GOLD);
        SoulTheme.panel(g, 18, 60, 156, 240);
        SoulTheme.panel(g, 182, 60, 248, 240);
        SoulTheme.panel(g, 438, 60, 164, 240);
        g.drawString(font, text("archive"), 28, 71, SoulTheme.GOLD, false);
        String total = Integer.toString(groups.size());
        g.drawString(font, total, 164 - font.width(total), 71, SoulTheme.MUTED, false);
        g.drawCenteredString(font, Component.literal((groups.isEmpty() ? 0 : page + 1) + " / " + (lastPage() + 1)), 96, 278, SoulTheme.MUTED);
        g.drawString(font, text("orders"), 448, 71, SoulTheme.GOLD, false);
        updateControls();
        renderSoul(g, partialTick);
        renderDetails(g);
        if (groups.isEmpty()) g.drawWordWrap(font, text("empty"), 30, 118, 132, SoulTheme.MUTED);
        super.render(g, (int)localX(mouseX), (int)localY(mouseY), partialTick);
        Component hint = text("preview_controls");
        for (SoulButton button : controls) if (button.isHoveredOrFocused() && !button.hint().getString().isEmpty()) hint = button.hint();
        g.drawWordWrap(font, hint, 185, 312, 239, SoulTheme.MUTED);
        g.pose().popPose();
    }
    private void metric(GuiGraphics g, int x, int width, Component label, String value, int accent) {
        g.fill(x, 14, x + width, 46, 0xFF0B1720);
        g.fill(x, 18, x + 1, 42, accent);
        g.drawString(font, label, x + 10, 18, SoulTheme.MUTED, false);
        g.drawString(font, value, x + 10, 32, accent, false);
    }
    private void renderSoul(GuiGraphics g, float partialTick) {
        var group = group();
        var row = row();
        String name = group == null ? text("unbound").getString() : name(group);
        g.drawCenteredString(font, Component.literal(fit(name, 222)), 306, 72, SoulTheme.LIGHT);
        g.fillGradient(PX, PY, PX + PW, PY + PH, 0xFF122C36, 0xFF0A161F);
        for (int i = 0; i < 13; i++) {
            int x = 204 + (i * 37) % 200, y = 100 + (int)((i * 19 + ticks * 0.16) % 112);
            SoulTheme.diamond(g, x, y, i % 3 == 0 ? 1 : 0, 0x4069C6B6);
        }
        SoulTheme.ellipse(g, 306, 217, 88, 15, 0xFF24484B);
        SoulTheme.ellipse(g, 306, 217, 75, 11, 0xFF468C80);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            SoulTheme.diamond(g, 306 + (int)(Math.cos(angle) * 81), 217 + (int)(Math.sin(angle) * 13), 1, SoulTheme.GOLD);
        }
        if (row != null && preview.available(selected)) {
            g.enableScissor((int)(originX + PX * scale), (int)(originY + PY * scale),
                (int)(originX + (PX + PW) * scale), (int)(originY + (PY + PH) * scale));
            try { preview.render(g, selected, 306, 212, PW, PH, partialTick); }
            finally { g.disableScissor(); }
        } else {
            SoulTheme.diamond(g, 306, 149, 15, 0xFF234349);
            SoulTheme.diamond(g, 306, 149, 6, SoulTheme.TEAL);
            g.drawCenteredString(font, row == null ? text("unbound")
                : text(preview.failed(selected) ? "preview_unavailable" : "preview_loading"), 306, 181, SoulTheme.MUTED);
        }
        g.fill(198, 236, 414, 237, SoulTheme.BORDER);
        if (group != null) {
            g.drawCenteredString(font, text("form_index", group.forms().indexOf(row) + 1, group.forms().size()), 306, 252, SoulTheme.LIGHT);
            g.drawCenteredString(font, text(group.forms().size() > 1 ? "captured_forms" : "capture_more_forms"), 306, 279, SoulTheme.MUTED);
        } else g.drawCenteredString(font, text("no_soul_selected"), 306, 252, SoulTheme.MUTED);
    }
    private void renderDetails(GuiGraphics g) {
        var row = row();
        var group = group();
        Component status = text("empty_short");
        int color = SoulTheme.MUTED;
        if (row != null && group != null) {
            if (group.active() != null) {
                status = text(group.active().id().equals(selected) ? "status_active" : "status_other_active"); color = SoulTheme.TEAL;
            } else {
                status = switch (row.status()) {
                    case RECOVERING -> text("recovering", cooldown(row));
                    case NO_SCYTHE -> text("status_no_scythe");
                    case LIMIT_REACHED -> text("status_limit");
                    case LOW_ENERGY -> text("status_low_energy");
                    default -> text("status_ready");
                };
                if (row.status() == SummonRules.Status.READY) color = SoulTheme.TEAL;
            }
        }
        SoulTheme.diamond(g, 452, 93, 2, color);
        g.drawString(font, fit(status.getString(), 129), 460, 89, color, false);
        g.drawString(font, text("summon_cost"), 448, 113, SoulTheme.MUTED, false);
        String cost = row == null ? "—" : Integer.toString(row.cost());
        g.drawString(font, cost, 592 - font.width(cost), 113, SoulTheme.TEAL, false);
        g.drawString(font, text("max_health"), 448, 137, SoulTheme.MUTED, false);
        String health = row == null ? "—" : java.math.BigDecimal.valueOf(row.maxHealth())
            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        g.drawString(font, health, 592 - font.width(health), 137, SoulTheme.GOLD, false);
        g.drawWordWrap(font, summonHint(), 449, 195, 140, SoulTheme.MUTED);
        g.fill(448, 225, 592, 226, SoulTheme.BORDER);
    }
    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }
    private double localX(double x) { return (x - originX) / scale; }
    private double localY(double y) { return (y - originY) / scale; }
    private boolean overPreview(double x, double y) { return x >= PX && x < PX + PW && y >= PY && y < PY + PH; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = localX(mouseX), y = localY(mouseY);
        if (button == 0 && overPreview(x, y)) { turning = true; return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (turning && button == 0) { preview.turn(dx / scale); return true; }
        return super.mouseDragged(localX(mouseX), localY(mouseY), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (turning && button == 0) { turning = false; return true; }
        return super.mouseReleased(localX(mouseX), localY(mouseY), button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = localX(mouseX), y = localY(mouseY);
        if (overPreview(x, y)) { preview.zoom(delta); return true; }
        if (x >= 18 && x < 174 && y >= 60 && y < 300) {
            page = net.minecraft.util.Mth.clamp(page + (delta < 0 ? 1 : -1), 0, lastPage());
            rebuildWidgets(); return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public void removed() { preview.clear(); }
    // Minecraft applies this pause to an unpublished integrated server, not multiplayer/LAN.
    @Override public boolean isPauseScreen() { return true; }
}
