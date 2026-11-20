// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;

public final class SoulBoltEntity extends AbstractArrow {
    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, Level world) {
        super(type, world);
    }

    public SoulBoltEntity(LivingEntity shooter, Level world, ItemStack weapon) {
        super(SoulCatcherPlus.SOUL_BOLT.get(), shooter, world, new ItemStack(net.minecraft.world.item.Items.ARROW), weapon);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (level().isClientSide()) return;
        super.onHit(hit);
        discard();
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        // The base hit handler adds a visible arrow to the target.
        if (!level().isClientSide() && getPierceLevel() == 0) {
            target.setArrowCount(Math.max(0, target.getArrowCount() - 1));
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

}
