// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public final class SoulBoltEntity extends AbstractArrow implements IEntityAdditionalSpawnData {
    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, Level world) {
        super(type, world);
    }

    public SoulBoltEntity(LivingEntity shooter, Level world) {
        super(SoulCatcherPlus.SOUL_BOLT.get(), shooter, world);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (level.isClientSide) return;
        super.onHit(hit);
        discard();
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        // The base hit handler adds a visible arrow to the target.
        if (!level.isClientSide && getPierceLevel() == 0) {
            target.setArrowCount(Math.max(0, target.getArrowCount() - 1));
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        Entity owner = getOwner();
        buffer.writeVarInt(owner == null ? -1 : owner.getId());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        setOwner(level.getEntity(buffer.readVarInt()));
    }
}
