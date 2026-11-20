// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.soulcatcherplus.furnace.SoulFurnaceMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;

public final class SoulFurnaceScreen extends AbstractContainerScreen<SoulFurnaceMenu> {
    public SoulFurnaceScreen(SoulFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 194, 185);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor pose, int x, int y, float partialTick) {
        super.extractRenderState(pose, x, y, partialTick);
    }
    @Override public void extractBackground(GuiGraphicsExtractor pose, int mouseX, int mouseY, float partialTick) {
        rect(pose, 0, 0, imageWidth, imageHeight, 0xFF39545D);
        rect(pose, 1, 1, imageWidth - 1, imageHeight - 1, 0xFF101D25);
        slot(pose, 47, 28);
        slot(pose, 47, 57);
        slot(pose, 131, 43);
        rect(pose, 75, 49, 118, 54, 0xFF243640);
        int fill = Mth.clamp(menu.progress(), 0, 1000) * 43 / 1000;
        rect(pose, 75, 49, 75 + fill, 54, 0xFF6DDDE9);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(pose, 17 + col * 18, 105 + row * 18);
        for (int col = 0; col < 9; col++) slot(pose, 17 + col * 18, 163);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor pose, int mouseX, int mouseY) {
        pose.centeredText(font, title, imageWidth / 2, 9, 0xFFB3E9E7);
        pose.text(font, Component.translatable("screen.soulcatcherplus.soul_furnace_energy", menu.energy(), menu.capacity()),
            17, 79, 0xFF6DDDE9);
        pose.text(font, playerInventoryTitle, 17, 94, 0xFF9DB3B7);
    }
    private void slot(GuiGraphicsExtractor pose, int x, int y) {
        rect(pose, x - 1, y - 1, x + 17, y + 17, 0xFF39545D);
        rect(pose, x, y, x + 16, y + 16, 0xFF09141C);
    }
    private void rect(GuiGraphicsExtractor pose, int x0, int y0, int x1, int y1, int color) {
        pose.fill(leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, color);
    }
}
