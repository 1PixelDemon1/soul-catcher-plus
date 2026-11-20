// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.projector.ProjectorSettings;
import dev.soulcatcherplus.projector.SoulProjectorContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;

/** Local edits commit on release; a normal container mirror provides the authoritative settings. */
public final class SoulProjectorScreen extends AbstractContainerScreen<SoulProjectorContainer> {
    private ProjectorSettings local = ProjectorSettings.DEFAULT, requested;
    private final List<SettingSlider> sliders = new ArrayList<>();
    private EditBox hex;
    private Button rotate;
    private boolean updating, dirty;
    private int ticks, sentAt;

    public SoulProjectorScreen(SoulProjectorContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 300, 224);
        inventoryLabelX = 69; inventoryLabelY = 124;
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.soulcatcherplus.projector_" + key, args);
    }
    @Override protected void init() {
        super.init();
        sliders.clear();
        local = menu.settings();
        for (int index = 0; index < 3; index++) {
            SettingSlider slider = new SettingSlider(index, leftPos + 90, topPos + 30 + index * 22);
            sliders.add(addRenderableWidget(slider));
        }
        hex = addRenderableWidget(new EditBox(font, leftPos + 124, topPos + 101, 86, 16, text("color")));
        hex.setMaxLength(7);
        hex.setResponder(value -> {
            String digits = value.startsWith("#") ? value.substring(1) : value;
            if (!updating && digits.matches("[0-9a-fA-F]{6}")) setColor(Integer.parseInt(digits, 16));
        });
        rotate = addRenderableWidget(Button.builder(text("rotate"), button -> {
            local = new ProjectorSettings(local.scale(), local.color(), local.angle(), local.opacity(), !local.rotating());
            dirty = true; refresh(); commit();
        }).bounds(leftPos + 16, topPos + 79, 64, 18).build());
        addRenderableWidget(Button.builder(text("reset"), button -> {
            local = ProjectorSettings.DEFAULT; dirty = true; refresh(); commit();
        }).bounds(leftPos + 238, topPos + 99, 46, 18).build());
        int[] colors = {0x59D9FF, 0x66E68C, 0xB47CFF, 0xFFFFFF};
        for (int index = 0; index < colors.length; index++) {
            final int color = colors[index];
            addRenderableWidget(new Button(leftPos + 17 + index * 15, topPos + 106, 12, 12, Component.empty(),
                    button -> setColor(color), caption -> caption.get().copy()) {
                @Override protected void extractContents(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
                    int x = getX(), y = getY();
                    pose.fill(x, y, x + width, y + height, isHovered() ? 0xFFFFFFFF : 0xFF53656A);
                    pose.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | color);
                }
            });
        }
        refresh();
    }
    private void setColor(int color) {
        local = new ProjectorSettings(local.scale(), color, local.angle(), local.opacity(), local.rotating());
        dirty = true; refresh(); commit();
    }
    private void refresh() {
        updating = true;
        try {
            if (hex != null) hex.setValue(String.format(Locale.ROOT, "%06X", local.color()));
            if (rotate != null) rotate.setMessage(text(local.rotating() ? "stop" : "rotate"));
            sliders.forEach(SettingSlider::refreshValue);
        } finally { updating = false; }
    }
    private void commit() {
        if (!dirty || minecraft == null || minecraft.getConnection() == null) return;
        SoulNetwork.sendToServer(new SoulNetwork.ProjectorUpdate(menu.containerId, local));
        requested = local; sentAt = ticks; dirty = false;
    }
    @Override protected void containerTick() {
        super.containerTick();
        ticks++;
        ProjectorSettings remote = menu.settings();
        if (requested != null) {
            if (remote.equals(requested) || ticks - sentAt > 100) requested = null;
            else return;
        }
        if (!dirty && !isDragging() && !hex.isFocused() && !remote.equals(local)) { local = remote; refresh(); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) { super.extractRenderState(pose, mouseX, mouseY, partialTick);
    }
    @Override public void extractBackground(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        pose.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF35515A);
        pose.fill(leftPos+1, topPos+1, leftPos+imageWidth-1, topPos+imageHeight-1, 0xFF101E27);
        pose.fill(leftPos+16, topPos+24, leftPos+284, topPos+25, 0xFF35515A);
        slot(pose, 40, 47);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) slot(pose, 69+column*18, 136+row*18);
        for (int column = 0; column < 9; column++) slot(pose, 69+column*18, 194);
        pose.fill(leftPos+215, topPos+100, leftPos+233, topPos+118, 0xFF53656A);
        pose.fill(leftPos+216, topPos+101, leftPos+232, topPos+117, 0xFF000000 | local.color());
    }
    private void slot(GuiGraphicsExtractor pose, int x, int y) {
        pose.fill(leftPos+x-1, topPos+y-1, leftPos+x+17, topPos+y+17, 0xFF53656A);
        pose.fill(leftPos+x, topPos+y, leftPos+x+16, topPos+y+16, 0xFF08141C);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor pose, int mouseX, int mouseY) {
        pose.centeredText(font, title, imageWidth / 2, 10, 0xA9DED9);
        Component scroll = text("scroll");
        pose.centeredText(font, scroll, 48, 33, 0x8DA6A6);
        pose.text(font, text("color"), 90, 105, 0x8DA6A6);
        pose.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x8DA6A6);
    }
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (isDragging() && getFocused() instanceof SettingSlider slider)
            return slider.mouseDragged(event, dx, dy);
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        boolean result = super.keyReleased(event); commit(); return result;
    }
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        boolean result = super.mouseReleased(event); commit(); return result;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (hex.isFocused() && key != InputConstants.KEY_ESCAPE) {
            if (hex.keyPressed(event) || minecraft.options.keyInventory.matches(event)) return true;
        }
        if (key == InputConstants.KEY_ESCAPE || minecraft.options.keyInventory.matches(event)) commit();
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) { return hex.charTyped(event) || super.charTyped(event); }
    @Override public void onClose() { commit(); super.onClose(); }
    @Override public void removed() { commit(); super.removed(); }

    private final class SettingSlider extends AbstractSliderButton {
        private final int kind;
        SettingSlider(int kind, int x, int y) { super(x, y, 194, 18, Component.empty(), 0); this.kind = kind; refreshValue(); }
        void refreshValue() {
            value = kind == 0 ? (local.scale()-25)/375.0 : kind == 1 ? local.angle()/359.0 : (local.opacity()-10)/90.0;
            updateMessage();
        }
        @Override protected void updateMessage() {
            setMessage(text(kind == 0 ? "scale" : kind == 1 ? "angle" : "opacity",
                kind == 0 ? local.scale() : kind == 1 ? local.angle() : local.opacity()));
        }
        @Override protected void applyValue() {
            local = new ProjectorSettings(kind == 0 ? (int)Math.round(25+value*375) : local.scale(), local.color(),
                kind == 1 ? (int)Math.round(value*359) : local.angle(), kind == 2 ? (int)Math.round(10+value*90) : local.opacity(), local.rotating());
            dirty = true; updateMessage();
        }
    }
}
