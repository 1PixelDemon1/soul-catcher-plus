// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.soulcatcherplus.furnace.SoulFurnaceMenu;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public final class SoulFurnaceScreen extends ContainerScreen<SoulFurnaceMenu> {
    public SoulFurnaceScreen(SoulFurnaceMenu menu, PlayerInventory inventory, ITextComponent title) {
        super(menu, inventory, title);
        imageWidth = 194;
        imageHeight = 185;
    }
    @Override public void render(MatrixStack pose, int x, int y, float partialTick) {
        renderBackground(pose);
        super.render(pose, x, y, partialTick);
        renderTooltip(pose, x, y);
    }
    @Override protected void renderBg(MatrixStack pose, float partialTick, int mouseX, int mouseY) {
        rect(pose, 0, 0, imageWidth, imageHeight, 0xFF39545D);
        rect(pose, 1, 1, imageWidth - 1, imageHeight - 1, 0xFF101D25);
        slot(pose, 47, 28);
        slot(pose, 47, 57);
        slot(pose, 131, 43);
        rect(pose, 75, 49, 118, 54, 0xFF243640);
        int fill = MathHelper.clamp(menu.progress(), 0, 1000) * 43 / 1000;
        rect(pose, 75, 49, 75 + fill, 54, 0xFF6DDDE9);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) slot(pose, 17 + col * 18, 105 + row * 18);
        for (int col = 0; col < 9; col++) slot(pose, 17 + col * 18, 163);
    }
    @Override protected void renderLabels(MatrixStack pose, int mouseX, int mouseY) {
        font.draw(pose, title, (imageWidth - font.width(title)) / 2f, 9, 0xFFB3E9E7);
        font.draw(pose, new TranslationTextComponent("screen.soulcatcherplus.soul_furnace_energy", menu.energy(), menu.capacity()),
            17, 79, 0xFF6DDDE9);
        font.draw(pose, inventory.getDisplayName(), 17, 94, 0xFF9DB3B7);
    }
    private void slot(MatrixStack pose, int x, int y) {
        rect(pose, x - 1, y - 1, x + 17, y + 17, 0xFF39545D);
        rect(pose, x, y, x + 16, y + 16, 0xFF09141C);
    }
    private void rect(MatrixStack pose, int x0, int y0, int x1, int y1, int color) {
        fill(pose, leftPos + x0, topPos + y0, leftPos + x1, topPos + y1, color);
    }
}
