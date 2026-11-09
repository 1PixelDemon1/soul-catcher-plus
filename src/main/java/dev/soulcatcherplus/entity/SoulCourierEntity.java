// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.menu.SoulCourierContainer;
import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Passive legendary porter. The chest is an access point, never a shared inventory. */
public final class SoulCourierEntity extends PathfinderMob {
    public static final float MAX_HEALTH = 8;
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(SoulCourierEntity.class, EntityDataSerializers.BOOLEAN);
    private int viewers;
    private float lid, previousLid;

    public SoulCourierEntity(EntityType<? extends SoulCourierEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeSupplier createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32).build();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OPEN, false);
    }

    @Override public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        // Saved prototype mobs and soul snapshots still carry the old 20-HP attribute.
        var health = getAttribute(Attributes.MAX_HEALTH);
        if (health.getBaseValue() == 20) {
            health.setBaseValue(MAX_HEALTH);
            setHealth(Math.min(getHealth(), getMaxHealth()));
        }
    }

    @Override protected void registerGoals() {
        // Keep the porter still while someone uses its chest, including during follow orders.
        goalSelector.addGoal(-1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { return entityData.get(OPEN); }
            @Override public void start() { navigation.stop(); }
            @Override public void tick() { navigation.stop(); }
        });
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive()) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            player.openMenu(new SimpleMenuProvider(
                (id, inventory, user) -> new SoulCourierContainer(id, inventory, this),
                Component.translatable("container.enderchest")));
        }
        return InteractionResult.SUCCESS;
    }

    public void startViewing() {
        if (viewers++ == 0) {
            entityData.set(OPEN, true);
            playSound(SoundEvents.ENDER_CHEST_OPEN, 0.5f, 0.9f + random.nextFloat() * 0.1f);
        }
    }

    public void stopViewing() {
        if (viewers > 0 && --viewers == 0) {
            entityData.set(OPEN, false);
            playSound(SoundEvents.ENDER_CHEST_CLOSE, 0.5f, 0.9f + random.nextFloat() * 0.1f);
        }
    }

    @Override public void tick() {
        super.tick();
        previousLid = lid;
        lid = Mth.clamp(lid + (entityData.get(OPEN) ? 0.1f : -0.1f), 0, 1);
    }

    public float lidProgress(float partialTick) { return Mth.lerp(partialTick, previousLid, lid); }
    @Override public boolean canAttack(LivingEntity target) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
