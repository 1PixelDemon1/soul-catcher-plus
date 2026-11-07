// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.menu.SoulCourierContainer;
import java.util.EnumSet;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;

/** Passive legendary porter. The chest is an access point, never a shared inventory. */
public final class SoulCourierEntity extends CreatureEntity {
    public static final float MAX_HEALTH = 8;
    private static final DataParameter<Boolean> OPEN = EntityDataManager.defineId(SoulCourierEntity.class, DataSerializers.BOOLEAN);
    private int viewers;
    private float lid, previousLid;

    public SoulCourierEntity(EntityType<? extends SoulCourierEntity> type, World world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeModifierMap createAttributes() {
        return MobEntity.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32).build();
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OPEN, false);
    }

    @Override public void readAdditionalSaveData(CompoundNBT tag) {
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
        goalSelector.addGoal(0, new SwimGoal(this));
        goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8));
        goalSelector.addGoal(7, new LookRandomlyGoal(this));
    }

    @Override protected ActionResultType mobInteract(PlayerEntity player, Hand hand) {
        if (!isAlive()) return ActionResultType.PASS;
        if (!level.isClientSide) {
            player.openMenu(new SimpleNamedContainerProvider(
                (id, inventory, user) -> new SoulCourierContainer(id, inventory, this),
                new TranslationTextComponent("container.enderchest")));
        }
        return ActionResultType.sidedSuccess(level.isClientSide);
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
        lid = MathHelper.clamp(lid + (entityData.get(OPEN) ? 0.1f : -0.1f), 0, 1);
    }

    public float lidProgress(float partialTick) { return MathHelper.lerp(partialTick, previousLid, lid); }
    @Override public boolean canAttack(LivingEntity target) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public IPacket<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
