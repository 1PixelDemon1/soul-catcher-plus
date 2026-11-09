// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.menu;

import dev.soulcatcherplus.entity.SoulCourierEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/** Server-only menu using the vanilla 27-slot client screen and the visitor's own storage. */
public final class SoulCourierContainer extends ChestMenu {
    private final SoulCourierEntity courier;
    private boolean closed;

    public SoulCourierContainer(int id, Inventory inventory, SoulCourierEntity courier) {
        super(MenuType.GENERIC_9x3, id, inventory, inventory.player.getEnderChestInventory(), 3);
        this.courier = courier;
        courier.startViewing();
    }

    @Override public boolean stillValid(Player player) {
        return courier.isAlive() && player.level == courier.level && player.distanceToSqr(courier) <= 64;
    }

    @Override public void removed(Player player) {
        if (closed) return;
        closed = true;
        super.removed(player);
        courier.stopViewing();
    }
}
