// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.data.SummonRules;
import dev.soulcatcherplus.data.SoulBalance;
import dev.soulcatcherplus.network.SoulNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Species archive, detached 3D model, captured-form selector and companion orders. */
public final class SoulScreen extends Screen {
    private static final int W = 620, H = 350;
    private static final int LIST_TOP = 112, LIST_BOTTOM = 294, CARD_STEP = 37, CARD_HEIGHT = 34;
    private static final int LIST_LEFT = 26, LIST_RIGHT = 156, SCROLL_LEFT = 160, SCROLL_RIGHT = 166;
    private static final int PX = 190, PY = 88, PW = 232, PH = 146;
    private SoulNetwork.State state;
    private final SoulPreview preview = new SoulPreview();
    private final List<SoulButton> controls = new ArrayList<>();
    private final List<CardControls> cards = new ArrayList<>();
    private final Map<String, UUID> rememberedForms = new HashMap<>();
    private List<SoulGroup> groups = com.google.common.collect.ImmutableList.of();
    private List<SoulGroup> filteredGroups = com.google.common.collect.ImmutableList.of();
    private String query = "", normalizedQuery = "";
    private TextFieldWidget searchBox;
    private UUID selected;
    private int ticks;
    private double scrollAmount, scrollGrabOffset;
    private boolean scrolling;
    private long receivedGameTime;
    private float scale = 1, originX, originY;
    private boolean turning;
    private SoulButton primary, previousForm, nextForm, follow, wait, guard, aggressive, scope, recallAll, forget;

    @com.github.bsideup.jabel.Desugar
    private record SoulGroup(String entityType, List<SoulNetwork.Row> forms) {
        SoulNetwork.Row active() { return forms.stream().filter(SoulNetwork.Row::active).findFirst().orElse(null); }
        boolean favorite() { return forms.get(0).favorite(); }
        boolean boss() { return forms.stream().anyMatch(form -> SoulBalance.isBossSoul(form.maxHealth())); }
    }
    @com.github.bsideup.jabel.Desugar
    private record CardControls(int index, String entityType, SoulButton body, SoulButton favorite) {}
    public SoulScreen(SoulNetwork.State state) {
        super(text("title"));
        this.state = state;
        receivedGameTime = worldTime();
        selected = state.selected();
        regroup();
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).equals(group())) scrollAmount = Math.min(i * CARD_STEP, maxScroll());
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
        List<String> before = filteredGroups.stream().map(SoulGroup::entityType).collect(com.google.common.collect.ImmutableList.toImmutableList());
        Map<String, List<SoulNetwork.Row>> map = new LinkedHashMap<>();
        state.rows().forEach(r -> map.computeIfAbsent(r.entityType(), t -> new ArrayList<>()).add(r));
        groups = map.entrySet().stream().map(e -> new SoulGroup(e.getKey(), com.google.common.collect.ImmutableList.copyOf(e.getValue())))
            .sorted(Comparator.comparing(SoulGroup::favorite).reversed()).collect(com.google.common.collect.ImmutableList.toImmutableList());
        filterGroups();
        if (state.rows().stream().noneMatch(r -> r.id().equals(selected))) selected = state.rows().isEmpty() ? null : state.rows().get(0).id();
        // New species or favorite ordering change the layout; ordinary refreshes do not.
        if (searchBox != null && !before.equals(filteredGroups.stream().map(SoulGroup::entityType).collect(com.google.common.collect.ImmutableList.toImmutableList()))) rebuildCards();
    }
    private static ITextComponent text(String key, Object... args) {
        return new net.minecraft.util.text.TranslationTextComponent("screen.soulcatcherplus." + key, args);
    }
    private SoulNetwork.Row row() { return state.rows().stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null); }
    private SoulGroup group() {
        return groups.stream().filter(g -> g.forms().stream().anyMatch(r -> r.id().equals(selected))).findFirst().orElse(null);
    }
    private String name(SoulGroup group) {
        var type = ForgeRegistries.ENTITIES.getValue(new ResourceLocation(group.entityType()));
        return type == null ? group.forms().get(0).name() : type.getDescription().getString();
    }
    private void filterGroups() {
        filteredGroups = normalizedQuery.isEmpty() ? groups : groups.stream().filter(group ->
            name(group).toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || group.entityType().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || group.forms().stream().anyMatch(form -> form.name().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
            .collect(com.google.common.collect.ImmutableList.toImmutableList());
    }
    private void searchChanged(String value) {
        query = value;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(normalizedQuery)) return;
        normalizedQuery = normalized;
        scrollAmount = 0;
        scrolling = false;
        filterGroups();
        rebuildCards();
    }
    private int contentHeight() { return Math.max(0, filteredGroups.size() * CARD_STEP - (CARD_STEP - CARD_HEIGHT)); }
    private int maxScroll() { return Math.max(0, contentHeight() - (LIST_BOTTOM - LIST_TOP)); }
    private int scrollOffset() { return (int)Math.round(scrollAmount); }
    private int firstCard() { return (scrollOffset() + CARD_STEP - CARD_HEIGHT) / CARD_STEP; }
    private int endCard() { return Math.min(filteredGroups.size(), (scrollOffset() + LIST_BOTTOM - LIST_TOP + CARD_STEP - 1) / CARD_STEP); }
    private void scrollTo(double amount) {
        scrollAmount = net.minecraft.util.math.MathHelper.clamp(amount, 0, maxScroll());
        if (cards.size() != endCard() - firstCard() || (!cards.isEmpty() && cards.get(0).index() != firstCard())) rebuildCards();
        else positionCards();
    }
    private void positionCards() {
        for (CardControls card : cards) {
            int y = LIST_TOP + card.index() * CARD_STEP - scrollOffset();
            card.body().y = y;
            card.favorite().y = y;
        }
    }
    private int thumbHeight() {
        int height = LIST_BOTTOM - LIST_TOP;
        return Math.min(height, Math.max(18, height * height / Math.max(1, contentHeight())));
    }
    private double thumbTop() {
        return LIST_TOP + (maxScroll() == 0 ? 0 : scrollAmount / maxScroll() * (LIST_BOTTOM - LIST_TOP - thumbHeight()));
    }
    private void dragScroll(double y) {
        int travel = LIST_BOTTOM - LIST_TOP - thumbHeight();
        if (travel > 0) scrollTo((y - LIST_TOP - scrollGrabOffset) / travel * maxScroll());
    }
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
        updateControls();
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
    private SoulButton button(int x, int y, int width, int height, ITextComponent label, SoulButton.Style style, Runnable action) {
        SoulButton button = new SoulButton(x, y, width, height, label, style, action);
        controls.add(button);
        return addButton(button);
    }

    private void confirmForget() {
        SoulGroup species = group();
        SoulNetwork.Row target = row();
        if (species == null || target == null) return;
        UUID id = target.id();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(SoulScreen.this);
            if (confirmed) send(SoulNetwork.Action.FORGET_FORM, id);
        }, text("forget_title", fit(name(species), 200)),
                text("forget_warning", species.forms().indexOf(target) + 1, species.forms().size()),
                text("forget_confirm"), text("forget_cancel")) {
            @Override protected void init() {
                super.init();
                int y = buttons.get(0).y;
                buttons.clear();
                children.clear();
                addButton(new SoulButton(width / 2 - 155, y, 150, 22, yesButton, SoulButton.Style.DANGER,
                    () -> callback.accept(true)));
                addButton(new SoulButton(width / 2 + 5, y, 150, 22, noButton, SoulButton.Style.SUBTLE,
                    () -> callback.accept(false)));
                setDelay(20);
            }
        });
    }

    @Override protected void init() {
        controls.clear();
        cards.clear();
        turning = scrolling = false;
        scale = Math.max(0.25f, Math.min(1.5f, Math.min((width - 12f) / W, (height - 12f) / H)));
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2;
        button(592, 19, 16, 20, new net.minecraft.util.text.StringTextComponent("×"), SoulButton.Style.SUBTLE, this::onClose).hint(text("close"));
        searchBox = addButton(new TextFieldWidget(font, 30, 92, 132, 12, text("search")));
        searchBox.setBordered(false);
        searchBox.setTextColor(SoulTheme.LIGHT);
        searchBox.setMaxLength(80);
        searchBox.setValue(query);
        searchBox.setResponder(this::searchChanged);
        previousForm = button(198, 245, 24, 22, new net.minecraft.util.text.StringTextComponent("‹"), SoulButton.Style.SUBTLE,
            () -> changeForm(-1)).hint(text("previous_form"));
        nextForm = button(390, 245, 24, 22, new net.minecraft.util.text.StringTextComponent("›"), SoulButton.Style.SUBTLE,
            () -> changeForm(1)).hint(text("next_form"));
        forget = button(234, 276, 144, 18, text("forget"), SoulButton.Style.DANGER, this::confirmForget)
            .hint(text("forget_hint"));
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
        rebuildCards();
        updateControls();
        preview.request(selected, ticks);
    }
    /** Rebuild only the filtered cards, retaining the text field, cursor and keyboard focus. */
    private void rebuildCards() {
        for (CardControls card : cards) {
            if (getFocused() == card.body() || getFocused() == card.favorite()) setFocused(null);
            removeWidget(card.body());
            removeWidget(card.favorite());
            controls.remove(card.body());
            controls.remove(card.favorite());
        }
        cards.clear();
        scrollAmount = net.minecraft.util.math.MathHelper.clamp(scrollAmount, 0, maxScroll());
        if (maxScroll() == 0) scrolling = false;
        for (int i = firstCard(); i < endCard(); i++) {
            SoulGroup group = filteredGroups.get(i);
            int y = LIST_TOP + i * CARD_STEP - scrollOffset();
            SoulButton body = archiveButton(LIST_LEFT, y, 108, new net.minecraft.util.text.StringTextComponent(name(group)), SoulButton.Style.CARD,
                () -> select(group)).subtitle(text("forms_short", group.forms().size())).selected(group.equals(group()))
                .lit(group.active() != null).boss(group.boss()).hint(text("choose_soul"));
            ITextComponent favoriteHint = text(group.favorite() ? "remove_favorite" : "add_favorite");
            SoulButton favorite = archiveButton(136, y, 20, favoriteHint, SoulButton.Style.FAVORITE,
                () -> send(SoulNetwork.Action.TOGGLE_FAVORITE, group.forms().get(0).id()))
                .selected(group.favorite()).hint(favoriteHint);
            cards.add(new CardControls(i, group.entityType(), body, favorite));
        }
    }
    /** Card widgets take part in focus/input, but are rendered separately inside the viewport. */
    private SoulButton archiveButton(int x, int y, int width, ITextComponent label, SoulButton.Style style, Runnable action) {
        SoulButton button = new SoulButton(x, y, width, CARD_HEIGHT, label, style, action);
        controls.add(button);
        return addWidget(button);
    }
    private boolean cardWidget(net.minecraft.client.gui.IGuiEventListener widget) {
        return cards.stream().anyMatch(card -> card.body() == widget || card.favorite() == widget);
    }
    private void renderCards(SoulGui g, double mouseX, double mouseY, float partialTick) {
        g.enableScissor((int)Math.ceil(originX + LIST_LEFT * scale), (int)Math.ceil(originY + LIST_TOP * scale),
            (int)Math.floor(originX + LIST_RIGHT * scale), (int)Math.floor(originY + LIST_BOTTOM * scale));
        try {
            int hoverY = overCards(mouseX, mouseY) ? (int)mouseY : Integer.MIN_VALUE;
            for (CardControls card : cards) {
                card.body().render(g.pose(), (int)mouseX, hoverY, partialTick);
                card.favorite().render(g.pose(), (int)mouseX, hoverY, partialTick);
            }
        } finally { g.disableScissor(); }
        if (maxScroll() > 0) {
            g.fill(SCROLL_LEFT, LIST_TOP, SCROLL_RIGHT, LIST_BOTTOM, SoulTheme.INK);
            int top = (int)Math.round(thumbTop());
            boolean hover = mouseX >= SCROLL_LEFT && mouseX < SCROLL_RIGHT && mouseY >= LIST_TOP && mouseY < LIST_BOTTOM;
            g.fill(SCROLL_LEFT, top, SCROLL_RIGHT, top + thumbHeight(), scrolling || hover ? SoulTheme.TEAL : SoulTheme.BORDER);
            g.fill(SCROLL_LEFT + 2, top + 3, SCROLL_RIGHT - 2, top + thumbHeight() - 3, SoulTheme.MUTED);
        }
    }
    private ITextComponent summonHint() {
        var row = row();
        var group = group();
        if (row == null || group == null) return text("empty_short");
        if (group.active() != null) return text(group.active().id().equals(selected) ? "recall_hint" : "other_form_active");
        return switch (row.status()) {
            case RECOVERING -> text("recovering", cooldown(row));
            case LIMIT_REACHED -> text("capacity_hint");
            case NO_SCYTHE -> text("scythe_needed");
            case NO_CAGE -> text("cage_needed");
            case CAGE_TOO_SMALL -> text("cage_too_small", row.cost());
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
        forget.active = group != null;
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
            if (button.x == 18 && button.y == 311) {
                button.setMessage(text(state.commandMode() ? "command" : "harvest"));
                button.hint(text(state.commandMode() ? "command_hint" : "harvest_hint"));
            }
        }
        for (CardControls card : cards) {
            SoulGroup species = groups.stream().filter(g -> g.entityType().equals(card.entityType())).findFirst().orElse(null);
            if (species == null) continue;
            card.body().selected(species.equals(group)).lit(species.active() != null).boss(species.boss())
                .subtitle(text("forms_short", species.forms().size()));
            ITextComponent favoriteHint = text(species.favorite() ? "remove_favorite" : "add_favorite");
            card.favorite().selected(species.favorite()).hint(favoriteHint).setMessage(favoriteHint);
        }
    }

    private void removeWidget(net.minecraft.client.gui.widget.Widget widget) {
        buttons.remove(widget);
        children.remove(widget);
    }
    @Override public void tick() {
        ticks++;
        searchBox.tick();
        preview.tick();
        preview.request(selected, ticks);
        if (ticks % 20 == 0) send(SoulNetwork.Action.REFRESH);
    }
    @Override public void render(com.mojang.blaze3d.matrix.MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        SoulGui g = new SoulGui(pose);
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
        metric(g, 333, 119, SoulCages.location(state.cageSlot()),
            state.cageCapacity() == 0 ? "—" : state.energy() + " / " + state.cageCapacity(), SoulTheme.TEAL);
        metric(g, 460, 125, text("capacity_label"), state.activeCount() + " / " + state.summonLimit(), SoulTheme.GOLD);
        SoulTheme.panel(g, 18, 60, 156, 240);
        SoulTheme.panel(g, 182, 60, 248, 240);
        SoulTheme.panel(g, 438, 60, 164, 240);
        g.drawString(font, text("archive"), 28, 71, SoulTheme.GOLD, false);
        String total = normalizedQuery.isEmpty() ? Integer.toString(groups.size()) : filteredGroups.size() + " / " + groups.size();
        g.drawString(font, total, 164 - font.width(total), 71, SoulTheme.MUTED, false);
        g.fill(26, 88, 166, 106, SoulTheme.INK);
        g.renderOutline(26, 88, 140, 18, searchBox.isFocused() ? SoulTheme.TEAL : SoulTheme.BORDER);
        g.drawString(font, text("orders"), 448, 71, SoulTheme.GOLD, false);
        updateControls();
        renderSoul(g, partialTick);
        renderDetails(g);
        if (filteredGroups.isEmpty()) g.drawWordWrap(font, text(groups.isEmpty() ? "empty" : "no_search_results"), 30, 139, 132, SoulTheme.MUTED);
        super.render(g.pose(), (int)localX(mouseX), (int)localY(mouseY), partialTick);
        renderCards(g, localX(mouseX), localY(mouseY), partialTick);
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused())
            g.drawString(font, text("search_hint"), 32, 94, SoulTheme.MUTED, false);
        ITextComponent hint = text("preview_controls");
        for (SoulButton button : controls) if ((button.isHovered() || button.isFocused()) && !button.hint().getString().isEmpty()) hint = button.hint();
        g.drawWordWrap(font, hint, 185, 312, 239, SoulTheme.MUTED);
        g.pose().popPose();
    }
    private void metric(SoulGui g, int x, int width, ITextComponent label, String value, int accent) {
        g.fill(x, 14, x + width, 46, 0xFF0B1720);
        g.fill(x, 18, x + 1, 42, accent);
        g.drawString(font, label, x + 10, 18, SoulTheme.MUTED, false);
        g.drawString(font, value, x + 10, 32, accent, false);
    }
    private void renderSoul(SoulGui g, float partialTick) {
        var group = group();
        var row = row();
        String name = group == null ? text("unbound").getString() : name(group);
        g.drawCenteredString(font, new net.minecraft.util.text.StringTextComponent(fit(name, 222)), 306, 72,
            group != null && group.boss() ? SoulTheme.BOSS_BLUE : SoulTheme.LIGHT);
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
        } else g.drawCenteredString(font, text("no_soul_selected"), 306, 252, SoulTheme.MUTED);
    }
    private void renderDetails(SoulGui g) {
        var row = row();
        var group = group();
        ITextComponent status = text("empty_short");
        int color = SoulTheme.MUTED;
        if (row != null && group != null) {
            if (group.active() != null) {
                status = text(group.active().id().equals(selected) ? "status_active" : "status_other_active"); color = SoulTheme.TEAL;
            } else {
                status = switch (row.status()) {
                    case RECOVERING -> text("recovering", cooldown(row));
                    case NO_SCYTHE -> text("status_no_scythe");
                    case NO_CAGE -> text("cage_missing");
                    case CAGE_TOO_SMALL -> text("status_cage_too_small");
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
    private boolean overCards(double x, double y) { return x >= LIST_LEFT && x < LIST_RIGHT && y >= LIST_TOP && y < LIST_BOTTOM; }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = localX(mouseX), y = localY(mouseY);
        if (button == 0 && maxScroll() > 0 && x >= SCROLL_LEFT && x < SCROLL_RIGHT && y >= LIST_TOP && y < LIST_BOTTOM) {
            searchBox.setFocus(false);
            setFocused(null);
            setDragging(false);
            scrolling = true;
            scrollGrabOffset = y >= thumbTop() && y < thumbTop() + thumbHeight() ? y - thumbTop() : thumbHeight() / 2.0;
            dragScroll(y);
            return true;
        }
        if (!overCards(x, y) && getChildAt(x, y).map(this::cardWidget).orElse(false)) return false;
        if (button == 0 && overPreview(x, y)) { searchBox.setFocus(false); setFocused(null); turning = true; return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (scrolling && button == 0) { dragScroll(localY(mouseY)); return true; }
        if (turning && button == 0) { preview.turn(dx / scale); return true; }
        return super.mouseDragged(localX(mouseX), localY(mouseY), button, dx / scale, dy / scale);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrolling && button == 0) { scrolling = false; return true; }
        if (turning && button == 0) { turning = false; return true; }
        return super.mouseReleased(localX(mouseX), localY(mouseY), button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double x = localX(mouseX), y = localY(mouseY);
        if (overPreview(x, y)) { preview.zoom(delta); return true; }
        if (x >= LIST_LEFT && x < SCROLL_RIGHT && y >= LIST_TOP && y < LIST_BOTTOM) {
            scrollTo(scrollAmount - delta * CARD_STEP);
            return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!searchBox.isFocused() && (getFocused() == null || cardWidget(getFocused()))) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> { scrollTo(scrollAmount - (LIST_BOTTOM - LIST_TOP)); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> { scrollTo(scrollAmount + (LIST_BOTTOM - LIST_TOP)); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> { scrollTo(0); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> { scrollTo(maxScroll()); return true; }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void removed() { preview.clear(); }
    // Minecraft applies this pause to an unpublished integrated server, not multiplayer/LAN.
    @Override public boolean isPauseScreen() { return true; }
}
