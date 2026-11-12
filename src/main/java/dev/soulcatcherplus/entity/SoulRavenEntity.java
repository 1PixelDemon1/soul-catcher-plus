// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;

import dev.soulcatcherplus.companion.SoulSpawnContext;
import dev.soulcatcherplus.entity.ai.SoulRavenCollectGoal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Passive legendary collector. Raw /summon birds remain unowned preview entities. */
public final class SoulRavenEntity extends PathfinderMob implements FlyingAnimal {
    private static final int CARGO_LIMIT = 8;
    private static final EntityDataAccessor<Boolean> PERCHED = SynchedEntityData.defineId(SoulRavenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> SOUL_OWNER = SynchedEntityData.defineId(SoulRavenEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private float flightBlend;
    private float previousFlightBlend;

    public SoulRavenEntity(EntityType<? extends SoulRavenEntity> type, Level world) {
        super(type, world);
        moveControl = new FlyingMoveControl(this, 10, false) {
            @Override public void tick() {
                if (isPerched()) {
                    setNoGravity(true);
                    setSpeed(0);
                    return;
                }
                super.tick();
            }
        };
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0f);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0f);
        setPersistenceRequired();
    }

    public static AttributeSupplier createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 4.0)
            .add(Attributes.MOVEMENT_SPEED, 0.2)
            .add(Attributes.FLYING_SPEED, 0.45)
            .add(Attributes.FOLLOW_RANGE, 20.0).build();
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PERCHED, false);
        entityData.define(SOUL_OWNER, Optional.empty());
    }

    public void configureSoul() {
        goalSelector.removeAllGoals();
        targetSelector.removeAllGoals();
        navigation.stop();
        setTarget(null);
        entityData.set(SOUL_OWNER, Optional.of(CompanionBrain.owner(this)));
        goalSelector.addGoal(0, new SoulRavenCollectGoal(this));
    }

    public boolean isPerched() { return entityData.get(PERCHED); }

    public Player perchOwner() {
        return entityData.get(SOUL_OWNER).map(level::getPlayerByUUID).orElse(null);
    }

    /** Shared server/client attachment point; the renderer also interpolates it between ticks. */
    public static Vec3 shoulderPosition(Player player, float partialTick) {
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
        return new Vec3(Mth.lerp(partialTick, player.xo, player.getX()) + Math.cos(yaw) * 0.42,
            Mth.lerp(partialTick, player.yo, player.getY()) + (player.isCrouching() ? 1.12 : 1.38),
            Mth.lerp(partialTick, player.zo, player.getZ()) + Math.sin(yaw) * 0.42);
    }

    public void setPerched(boolean perched) {
        boolean changed = isPerched() != perched;
        entityData.set(PERCHED, perched);
        noPhysics = perched;
        if (changed || perched) setNoGravity(perched);
        if (perched) {
            navigation.stop();
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> parameter) {
        super.onSyncedDataUpdated(parameter);
        if (PERCHED.equals(parameter)) noPhysics = isPerched();
    }

    public boolean hasCargo() { return !getPassengers().isEmpty(); }
    public boolean canCarryMore() { return getPassengers().size() < CARGO_LIMIT; }

    public boolean takeCargo(Entity cargo) {
        if (!cargo.isAlive() || cargo.isPassenger() || !canAddPassenger(cargo) || !cargo.startRiding(this)) return false;
        if (cargo instanceof ItemEntity item) {
            RavenItemDelivery.protect(item);
            item.setNeverPickUp();
        }
        setPerched(false);
        positionRider(cargo);
        return true;
    }

    /** Leave the original item/remnant in the world; never copy or respawn a carried stack. */
    public boolean releaseCargo() {
        boolean released = true;
        // Successful dismounts mutate the passenger list, including during native callbacks.
        for (Entity cargo : new ArrayList<>(getPassengers())) {
            if (!releaseCargo(cargo)) released = false;
        }
        return released;
    }

    public boolean releaseCargo(Entity cargo) {
        if (cargo.getVehicle() != this) return false;
        cargo.stopRiding();
        if (cargo.isAlive() && cargo.getVehicle() == this) return false; // A Forge integration may reject dismounting.
        cargo.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    @Override protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // All native dismount paths must unlock loot, including portals and external ejects.
        if (passenger instanceof ItemEntity item) item.setNoPickUpDelay();
    }

    @Override protected boolean canAddPassenger(Entity passenger) {
        return canCarryMore() && (passenger instanceof ItemEntity || passenger instanceof SoulRemnant);
    }

    @Override public void positionRider(Entity passenger) {
        int index = getPassengers().indexOf(passenger);
        if (index < 0) return;
        double angle = index * Math.PI / 4;
        double radius = index == 0 ? 0 : 0.12;
        passenger.setPos(getX() + Math.cos(angle) * radius, getY() - 0.2 - (index / 4) * 0.04,
            getZ() + Math.sin(angle) * radius);
    }

    @Override public boolean canAttack(LivingEntity target) { return false; }
    @Override public boolean doHurtTarget(Entity target) { return false; }
    @Override public boolean isPushable() { return !isPerched() && super.isPushable(); }
    @Override public boolean isPickable() { return !isPerched() && super.isPickable(); }

    @Override public void die(DamageSource source) {
        releaseCargo();
        super.die(source);
    }

    @Override public void remove(RemovalReason reason) {
        // Chunk unload preserves native Passengers NBT; death, recall and stale souls release it.
        if (reason.shouldDestroy() && !releaseCargo()) {
            // If another mod refuses dismounting, keep the real cargo's vehicle until retry.
            // Loot remains pickable; never delete its only serialized parent underneath it.
            for (Entity cargo : getPassengers()) {
                if (cargo instanceof ItemEntity item) item.setNoPickUpDelay();
            }
            return;
        }
        super.remove(reason);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override protected PathNavigation createNavigation(Level world) {
        FlyingPathNavigation navigator = new FlyingPathNavigation(this, world);
        navigator.setCanOpenDoors(false);
        navigator.setCanFloat(true);
        navigator.setCanPassDoors(true);
        return navigator;
    }

    @Override public void tick() {
        super.tick();
        if (isPerched()) {
            Player owner = perchOwner();
            if (owner != null) {
                Vec3 shoulder = shoulderPosition(owner, 1);
                setPos(shoulder.x, shoulder.y, shoulder.z);
                setDeltaMovement(Vec3.ZERO);
                // Perched birds use the owner's two samples, not a stale network head turn.
                setYRot(yBodyRot = owner.yBodyRot);
                yRotO = yBodyRotO = owner.yBodyRotO;
                yHeadRot = owner.yHeadRot;
                yHeadRotO = owner.yHeadRotO;
                setXRot(0);
                xRotO = 0;
            }
        }
        if (level.isClientSide) {
            previousFlightBlend = flightBlend;
            float target = isPerched() || isOnGround() ? 0.0f : 1.0f;
            flightBlend += Mth.clamp(target - flightBlend, -0.15f, 0.15f);
        }
    }

    @Override public boolean isFlying() { return !isPerched() && !isOnGround(); }

    public float getFlightBlend(float partialTick) {
        return Mth.lerp(partialTick, previousFlightBlend, flightBlend);
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override protected void checkFallDamage(double distance, boolean onGround, BlockState state, BlockPos pos) {
        // Native bird movement may land from flight; landing must not accumulate fall damage.
        fallDistance = 0.0f;
    }

    @Override public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
