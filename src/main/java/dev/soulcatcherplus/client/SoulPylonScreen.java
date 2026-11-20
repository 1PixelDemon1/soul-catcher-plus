// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.pylon.SoulPylonContainer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Compact stone-and-gold inventory, with the same palette as the collection screen. */
public final class SoulPylonScreen extends ContainerScreen<SoulPylonContainer> {
    private static final int PANEL = 0xFF101E27, INNER = 0xFF0A141C, EDGE = 0xFF324A50;
    private static final int GOLD = 0xFFC5AF77, MUTED = 0xFF8DA6A6, CYAN = 0xFF7CE4D7;

    public SoulPylonScreen(SoulPylonContainer menu, PlayerInventory player, ITextComponent title) {
        super(menu, player, title);
        imageWidth = 230;
        imageHeight = 207;
        inventoryLabelX = 34;
        inventoryLabelY = 107;
    }

    @Override public void render(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
    }
    @Override protected void renderBg(MatrixStack pose, float partialTick, int mouseX, int mouseY) {
        fill(pose, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, EDGE);
        fill(pose, leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL);
        for (int x : new int[]{leftPos, leftPos + imageWidth - 10}) {
            fill(pose, x, topPos, x + 10, topPos + 2, GOLD);
            fill(pose, x, topPos + imageHeight - 2, x + 10, topPos + imageHeight, GOLD);
        }
        fill(pose, leftPos + 16, topPos + 26, leftPos + imageWidth - 16, topPos + 27, EDGE);
        slot(pose, SoulPylonContainer.CAGE_X, SoulPylonContainer.CONTENT_Y, GOLD);
        slot(pose, SoulPylonContainer.SCROLL_X, SoulPylonContainer.CONTENT_Y, GOLD);
        fill(pose, leftPos + 96, topPos + 56, leftPos + 134, topPos + 58, EDGE);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            slot(pose, 34 + column * 18, 119 + row * 18, EDGE);
        for (int column = 0; column < 9; column++) slot(pose, 34 + column * 18, 177, EDGE);
    }
    private void slot(MatrixStack pose, int x, int y, int border) {
        fill(pose, leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, border);
        fill(pose, leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, INNER);
    }
    @Override protected void renderLabels(MatrixStack pose, int mouseX, int mouseY) {
        centered(pose, title, imageWidth / 2, 11, GOLD);
        centered(pose, text("cage"), SoulPylonContainer.CAGE_X + 8, 35, MUTED);
        centered(pose, text("scroll"), SoulPylonContainer.SCROLL_X + 8, 35, MUTED);
        ITextComponent energy = menu.cage().isEmpty() ? text("insert")
            : text("energy", SoulCage.energy(menu.cage()), SoulCage.capacity(menu.cage()));
        centered(pose, energy, imageWidth / 2, 79, CYAN);
        centered(pose, text(menu.complete() ? "ready" : "needs_items"), imageWidth / 2, 93, MUTED);
        font.draw(pose, inventory.getDisplayName(), inventoryLabelX, inventoryLabelY, MUTED);
    }
    private void centered(MatrixStack pose, ITextComponent text, int x, int y, int color) {
        font.draw(pose, text, x - font.width(text) / 2f, y, color);
    }
    private static ITextComponent text(String key, Object... args) {
        return new TranslationTextComponent("screen.soulcatcherplus.pylon_" + key, args);
    }
}
