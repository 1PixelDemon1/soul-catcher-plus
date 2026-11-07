// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.LegacyGoals;
import dev.soulcatcherplus.companion.SoulSpawnContext;
import dev.soulcatcherplus.entity.ai.SoulRavenCollectGoal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.entity.passive.IFlyingAnimal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** Passive legendary collector. Raw /summon birds remain unowned preview entities. */
public final class SoulRavenEntity extends CreatureEntity implements IFlyingAnimal {
    private static final int CARGO_LIMIT = 8;
    private static final DataParameter<Boolean> PERCHED = EntityDataManager.defineId(SoulRavenEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Optional<UUID>> SOUL_OWNER = EntityDataManager.defineId(SoulRavenEntity.class, DataSerializers.OPTIONAL_UUID);
    private float flightBlend;
    private float previousFlightBlend;

    public SoulRavenEntity(EntityType<? extends SoulRavenEntity> type, World world) {
        super(type, world);
        moveControl = new FlyingMovementController(this, 10, false) {
            @Override public void tick() {
                if (isPerched()) {
                    setNoGravity(true);
                    setSpeed(0);
                    return;
                }
                super.tick();
            }
        };
        setPathfindingMalus(PathNodeType.DANGER_FIRE, -1.0f);
        setPathfindingMalus(PathNodeType.DAMAGE_FIRE, -1.0f);
        setPersistenceRequired();
    }

    public static AttributeModifierMap createAttributes() {
        return MobEntity.createMobAttributes()
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
        LegacyGoals.clear(goalSelector);
        LegacyGoals.clear(targetSelector);
        navigation.stop();
        setTarget(null);
        entityData.set(SOUL_OWNER, Optional.of(CompanionBrain.owner(this)));
        goalSelector.addGoal(0, new SoulRavenCollectGoal(this));
    }

    public boolean isPerched() { return entityData.get(PERCHED); }

    public PlayerEntity perchOwner() {
        return entityData.get(SOUL_OWNER).map(level::getPlayerByUUID).orElse(null);
    }

    /** Shared server/client attachment point; the renderer also interpolates it between ticks. */
    public static Vector3d shoulderPosition(PlayerEntity player, float partialTick) {
        double yaw = Math.toRadians(MathHelper.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
        return new Vector3d(MathHelper.lerp(partialTick, player.xo, player.getX()) + Math.cos(yaw) * 0.42,
            MathHelper.lerp(partialTick, player.yo, player.getY()) + (player.isCrouching() ? 1.12 : 1.38),
            MathHelper.lerp(partialTick, player.zo, player.getZ()) + Math.sin(yaw) * 0.42);
    }

    public void setPerched(boolean perched) {
        boolean changed = isPerched() != perched;
        entityData.set(PERCHED, perched);
        noPhysics = perched;
        if (changed || perched) setNoGravity(perched);
        if (perched) {
            navigation.stop();
            setDeltaMovement(Vector3d.ZERO);
        }
    }

    @Override public void onSyncedDataUpdated(DataParameter<?> parameter) {
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
        cargo.setDeltaMovement(Vector3d.ZERO);
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

    @Override public void remove(boolean keepData) {
        // Chunk unload preserves native Passengers NBT; death, recall and stale souls release it.
        if (!SoulSpawnContext.unloading(this) && !releaseCargo()) {
            // If another mod refuses dismounting, keep the real cargo's vehicle until retry.
            // Loot remains pickable; never delete its only serialized parent underneath it.
            for (Entity cargo : getPassengers()) {
                if (cargo instanceof ItemEntity item) item.setNoPickUpDelay();
            }
            return;
        }
        super.remove(keepData);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new SwimGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(2, new LookAtGoal(this, PlayerEntity.class, 6.0f));
        goalSelector.addGoal(3, new LookRandomlyGoal(this));
    }

    @Override protected PathNavigator createNavigation(World world) {
        FlyingPathNavigator navigator = new FlyingPathNavigator(this, world);
        navigator.setCanOpenDoors(false);
        navigator.setCanFloat(true);
        navigator.setCanPassDoors(true);
        return navigator;
    }

    @Override public void tick() {
        super.tick();
        if (isPerched()) {
            PlayerEntity owner = perchOwner();
            if (owner != null) {
                Vector3d shoulder = shoulderPosition(owner, 1);
                setPos(shoulder.x, shoulder.y, shoulder.z);
                setDeltaMovement(Vector3d.ZERO);
                yRot = yBodyRot = owner.yBodyRot;
                yHeadRot = owner.yHeadRot;
                xRot = 0;
            }
        }
        if (level.isClientSide) {
            previousFlightBlend = flightBlend;
            float target = isPerched() || isOnGround() ? 0.0f : 1.0f;
            flightBlend += MathHelper.clamp(target - flightBlend, -0.15f, 0.15f);
        }
    }

    public float getFlightBlend(float partialTick) {
        return MathHelper.lerp(partialTick, previousFlightBlend, flightBlend);
    }

    @Override public boolean causeFallDamage(float distance, float multiplier) { return false; }

    @Override protected void checkFallDamage(double distance, boolean onGround, BlockState state, BlockPos pos) {
        // Native bird movement may land from flight; landing must not accumulate fall damage.
        fallDistance = 0.0f;
    }

    @Override public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
