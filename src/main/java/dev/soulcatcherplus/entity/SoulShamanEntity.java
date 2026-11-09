// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;

/** Passive support soul. Its aura supports only its owner and that owner's active souls. */
public final class SoulShamanEntity extends PathfinderMob {
    public static final float MAX_HEALTH = 8;
    public static final int CAST_PERIOD_TICKS = 140;
    public static final int CAST_DURATION_TICKS = 36;
    private static final int CAST_IMPACT_TICK = 18;
    private static final int AURA_DURATION_TICKS = 160;
    private static final double AURA_RADIUS = 6;
    private static final EntityDataAccessor<Boolean> CASTING = SynchedEntityData.defineId(SoulShamanEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> CAST_STARTED_AT = SynchedEntityData.defineId(SoulShamanEntity.class, EntityDataSerializers.INT);
    private long nextCastAt;

    public SoulShamanEntity(EntityType<? extends SoulShamanEntity> type, Level world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeSupplier createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32).build();
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CASTING, false);
        builder.define(CAST_STARTED_AT, 0);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Level time keeps observers aligned even when they begin tracking mid-cast. */
    public float castPhase(float age) {
        if (!entityData.get(CASTING)) return CAST_DURATION_TICKS;
        // Subtract in int space so a world-time wrap does not interrupt a short cast.
        int elapsed = (int)level().getGameTime() - entityData.get(CAST_STARTED_AT);
        return Mth.clamp(elapsed + Mth.clamp(age - tickCount, 0, 1), 0, CAST_DURATION_TICKS);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide() || !isAlive()) return;
        long now = level().getGameTime();
        if (entityData.get(CASTING)) {
            int elapsed = (int)now - entityData.get(CAST_STARTED_AT);
            if (elapsed == CAST_IMPACT_TICK) applyAura();
            if (elapsed >= CAST_DURATION_TICKS) entityData.set(CASTING, false);
        }
        // While waiting for an eligible owner, check only once a second.
        if (tickCount % 20 == 0 && now >= nextCastAt && auraOwner() != null) {
            entityData.set(CAST_STARTED_AT, (int)now);
            entityData.set(CASTING, true);
            nextCastAt = now + CAST_PERIOD_TICKS;
        }
    }

    private ServerPlayer auraOwner() {
        if (!(level() instanceof ServerLevel) || CompanionBrain.activeEntry(this) == null) return null;
        ServerPlayer owner = level().getServer().getPlayerList().getPlayer(CompanionBrain.owner(this));
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == level()
            && distanceToSqr(owner) <= AURA_RADIUS * AURA_RADIUS ? owner : null;
    }

    private void applyAura() {
        // Recheck at impact: leaving, recalling or changing dimensions must cancel the buff.
        ServerPlayer owner = auraOwner();
        if (owner == null) return;
        grantAura(owner);
        for (Mob soul : level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(AURA_RADIUS),
                mob -> mob.isAlive() && CompanionBrain.isSoul(mob)
                    && owner.getUUID().equals(CompanionBrain.owner(mob))
                    && mob.distanceToSqr(owner) <= AURA_RADIUS * AURA_RADIUS
                    && CompanionBrain.activeEntry(mob) != null)) {
            grantAura(soul);
        }
        playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.35f, 1.25f);
    }

    private static void grantAura(LivingEntity recipient) {
        // Normal effect merging preserves stronger buffs and native immunities (including undead regeneration).
        recipient.addEffect(new MobEffectInstance(MobEffects.STRENGTH, AURA_DURATION_TICKS, 0, true, false, true));
        recipient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, AURA_DURATION_TICKS, 0, true, false, true));
    }

    @Override public boolean canAttack(LivingEntity target) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
