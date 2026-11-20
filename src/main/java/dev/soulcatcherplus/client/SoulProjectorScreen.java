// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.network.SoulNetwork;
import dev.soulcatcherplus.projector.ProjectorSettings;
import dev.soulcatcherplus.projector.SoulProjectorContainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.AbstractSlider;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;

/** Local edits commit on release; a normal container mirror provides the authoritative settings. */
public final class SoulProjectorScreen extends ContainerScreen<SoulProjectorContainer> {
    private ProjectorSettings local = ProjectorSettings.DEFAULT, requested;
    private final List<SettingSlider> sliders = new ArrayList<>();
    private TextFieldWidget hex;
    private Button rotate;
    private boolean updating, dirty;
    private int ticks, sentAt;

    public SoulProjectorScreen(SoulProjectorContainer menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 300; imageHeight = 224;
        inventoryLabelX = 69; inventoryLabelY = 124;
    }
    private static ITextComponent text(String key, Object... args) {
        return new TranslationTextComponent("screen.soulcatcherplus.projector_" + key, args);
    }
    @Override protected void init() {
        super.init();
        sliders.clear();
        local = menu.settings();
        for (int index = 0; index < 3; index++) {
            SettingSlider slider = new SettingSlider(index, leftPos + 90, topPos + 30 + index * 22);
            sliders.add(addButton(slider));
        }
        hex = addButton(new TextFieldWidget(font, leftPos + 124, topPos + 101, 86, 16, text("color")));
        hex.setMaxLength(7);
        hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        hex.setResponder(value -> {
            String digits = value.startsWith("#") ? value.substring(1) : value;
            if (!updating && digits.length() == 6) setColor(Integer.parseInt(digits, 16));
        });
        rotate = addButton(new Button(leftPos + 16, topPos + 79, 64, 18, text("rotate"), button -> {
            local = new ProjectorSettings(local.scale(), local.color(), local.angle(), local.opacity(), !local.rotating());
            dirty = true; refresh(); commit();
        }));
        addButton(new Button(leftPos + 238, topPos + 99, 46, 18, text("reset"), button -> {
            local = ProjectorSettings.DEFAULT; dirty = true; refresh(); commit();
        }));
        int[] colors = {0x59D9FF, 0x66E68C, 0xB47CFF, 0xFFFFFF};
        for (int index = 0; index < colors.length; index++) {
            final int color = colors[index];
            addButton(new Button(leftPos + 17 + index * 15, topPos + 106, 12, 12, StringTextComponent.EMPTY,
                    button -> setColor(color)) {
                @Override public void renderButton(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
                    fill(pose, x, y, x + width, y + height, isHovered() ? 0xFFFFFFFF : 0xFF53656A);
                    fill(pose, x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000 | color);
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
        SoulNetwork.CHANNEL.sendToServer(new SoulNetwork.ProjectorUpdate(menu.containerId, local));
        requested = local; sentAt = ticks; dirty = false;
    }
    @Override public void tick() {
        super.tick();
        ticks++; hex.tick();
        ProjectorSettings remote = menu.settings();
        if (requested != null) {
            if (remote.equals(requested) || ticks - sentAt > 100) requested = null;
            else return;
        }
        if (!dirty && !isDragging() && !hex.isFocused() && !remote.equals(local)) { local = remote; refresh(); }
    }
    @Override public void render(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose); super.render(pose, mouseX, mouseY, partialTick); renderTooltip(pose, mouseX, mouseY);
    }
    @Override protected void renderBg(MatrixStack pose, float partialTick, int mouseX, int mouseY) {
        fill(pose, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF35515A);
        fill(pose, leftPos+1, topPos+1, leftPos+imageWidth-1, topPos+imageHeight-1, 0xFF101E27);
        fill(pose, leftPos+16, topPos+24, leftPos+284, topPos+25, 0xFF35515A);
        slot(pose, 40, 47);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) slot(pose, 69+column*18, 136+row*18);
        for (int column = 0; column < 9; column++) slot(pose, 69+column*18, 194);
        fill(pose, leftPos+215, topPos+100, leftPos+233, topPos+118, 0xFF53656A);
        fill(pose, leftPos+216, topPos+101, leftPos+232, topPos+117, 0xFF000000 | local.color());
    }
    private void slot(MatrixStack pose, int x, int y) {
        fill(pose, leftPos+x-1, topPos+y-1, leftPos+x+17, topPos+y+17, 0xFF53656A);
        fill(pose, leftPos+x, topPos+y, leftPos+x+16, topPos+y+16, 0xFF08141C);
    }
    @Override protected void renderLabels(MatrixStack pose, int mouseX, int mouseY) {
        font.draw(pose, title, (imageWidth-font.width(title))/2f, 10, 0xA9DED9);
        ITextComponent scroll = text("scroll");
        font.draw(pose, scroll, 48-font.width(scroll)/2f, 33, 0x8DA6A6);
        font.draw(pose, text("color"), 90, 105, 0x8DA6A6);
        font.draw(pose, inventory.getDisplayName(), inventoryLabelX, inventoryLabelY, 0x8DA6A6);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (isDragging() && getFocused() instanceof SettingSlider slider)
            return slider.mouseDragged(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        boolean result = super.keyReleased(key, scan, modifiers); commit(); return result;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        boolean result = super.mouseReleased(x, y, button); commit(); return result;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (hex.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) {
            if (hex.keyPressed(key, scan, modifiers) || minecraft.options.keyInventory.matches(key, scan)) return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(key, scan)) commit();
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) { return hex.charTyped(character, modifiers) || super.charTyped(character, modifiers); }
    @Override public void onClose() { commit(); super.onClose(); }
    @Override public void removed() { commit(); super.removed(); }

    private final class SettingSlider extends AbstractSlider {
        private final int kind;
        SettingSlider(int kind, int x, int y) { super(x, y, 194, 18, StringTextComponent.EMPTY, 0); this.kind = kind; refreshValue(); }
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
