// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.SoulCatcherPlus;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;

public final class SoulBoltEntity extends AbstractArrowEntity implements IEntityAdditionalSpawnData {
    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, World world) {
        super(type, world);
    }

    public SoulBoltEntity(LivingEntity shooter, World world) {
        super(SoulCatcherPlus.SOUL_BOLT.get(), shooter, world);
        pickup = PickupStatus.DISALLOWED;
    }

    @Override
    protected void onHit(RayTraceResult hit) {
        if (level.isClientSide) return;
        super.onHit(hit);
        remove();
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
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(PacketBuffer buffer) {
        Entity owner = getOwner();
        buffer.writeVarInt(owner == null ? -1 : owner.getId());
    }

    @Override
    public void readSpawnData(PacketBuffer buffer) {
        setOwner(level.getEntity(buffer.readVarInt()));
    }
}
