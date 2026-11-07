// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.menu;

import dev.soulcatcherplus.entity.SoulCourierEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ContainerType;

/** Server-only menu using the vanilla 27-slot client screen and the visitor's own storage. */
public final class SoulCourierContainer extends ChestContainer {
    private final SoulCourierEntity courier;
    private boolean closed;

    public SoulCourierContainer(int id, PlayerInventory inventory, SoulCourierEntity courier) {
        super(ContainerType.GENERIC_9x3, id, inventory, inventory.player.getEnderChestInventory(), 3);
        this.courier = courier;
        courier.startViewing();
    }

    @Override public boolean stillValid(PlayerEntity player) {
        return courier.isAlive() && player.level == courier.level && player.distanceToSqr(courier) <= 64;
    }

    @Override public void removed(PlayerEntity player) {
        if (closed) return;
        closed = true;
        super.removed(player);
        courier.stopViewing();
    }
}
