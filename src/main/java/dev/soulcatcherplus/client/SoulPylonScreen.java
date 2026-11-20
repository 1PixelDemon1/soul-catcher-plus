// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.soulcatcherplus.item.SoulCage;
import dev.soulcatcherplus.pylon.SoulPylonContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

/** Compact stone-and-gold inventory, with the same palette as the collection screen. */
public final class SoulPylonScreen extends AbstractContainerScreen<SoulPylonContainer> {
    private static final int PANEL = 0xFF101E27, INNER = 0xFF0A141C, EDGE = 0xFF324A50;
    private static final int GOLD = 0xFFC5AF77, MUTED = 0xFF8DA6A6, CYAN = 0xFF7CE4D7;

    public SoulPylonScreen(SoulPylonContainer menu, Inventory player, Component title) {
        super(menu, player, title, 230, 207);
        inventoryLabelX = 34;
        inventoryLabelY = 107;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(pose, mouseX, mouseY, partialTick);
    }
    @Override public void extractBackground(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        pose.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, EDGE);
        pose.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL);
        for (int x : new int[]{leftPos, leftPos + imageWidth - 10}) {
            pose.fill(x, topPos, x + 10, topPos + 2, GOLD);
            pose.fill(x, topPos + imageHeight - 2, x + 10, topPos + imageHeight, GOLD);
        }
        pose.fill(leftPos + 16, topPos + 26, leftPos + imageWidth - 16, topPos + 27, EDGE);
        slot(pose, SoulPylonContainer.CAGE_X, SoulPylonContainer.CONTENT_Y, GOLD);
        slot(pose, SoulPylonContainer.SCROLL_X, SoulPylonContainer.CONTENT_Y, GOLD);
        pose.fill(leftPos + 96, topPos + 56, leftPos + 134, topPos + 58, EDGE);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            slot(pose, 34 + column * 18, 119 + row * 18, EDGE);
        for (int column = 0; column < 9; column++) slot(pose, 34 + column * 18, 177, EDGE);
    }
    private void slot(GuiGraphicsExtractor pose, int x, int y, int border) {
        pose.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, border);
        pose.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, INNER);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor pose, int mouseX, int mouseY) {
        centered(pose, title, imageWidth / 2, 11, GOLD);
        centered(pose, text("cage"), SoulPylonContainer.CAGE_X + 8, 35, MUTED);
        centered(pose, text("scroll"), SoulPylonContainer.SCROLL_X + 8, 35, MUTED);
        Component energy = menu.cage().isEmpty() ? text("insert")
            : text("energy", SoulCage.energy(menu.cage()), SoulCage.capacity(menu.cage()));
        centered(pose, energy, imageWidth / 2, 79, CYAN);
        centered(pose, text(menu.complete() ? "ready" : "needs_items"), imageWidth / 2, 93, MUTED);
        pose.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED);
    }
    private void centered(GuiGraphicsExtractor pose, Component text, int x, int y, int color) {
        pose.centeredText(font, text, x, y, color);
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("screen.soulcatcherplus.pylon_" + key, args);
    }
}
