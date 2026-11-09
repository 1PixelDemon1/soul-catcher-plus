// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.entity;

import dev.soulcatcherplus.companion.CompanionBrain;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;

/** Passive support soul. Its aura supports only its owner and that owner's active souls. */
public final class SoulShamanEntity extends CreatureEntity {
    public static final float MAX_HEALTH = 8;
    public static final int CAST_PERIOD_TICKS = 140;
    public static final int CAST_DURATION_TICKS = 36;
    private static final int CAST_IMPACT_TICK = 18;
    private static final int AURA_DURATION_TICKS = 160;
    private static final double AURA_RADIUS = 6;
    private static final DataParameter<Boolean> CASTING = EntityDataManager.defineId(SoulShamanEntity.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> CAST_STARTED_AT = EntityDataManager.defineId(SoulShamanEntity.class, DataSerializers.INT);
    private long nextCastAt;

    public SoulShamanEntity(EntityType<? extends SoulShamanEntity> type, World world) {
        super(type, world);
        setPersistenceRequired();
    }

    public static AttributeModifierMap createAttributes() {
        return MobEntity.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.FOLLOW_RANGE, 32).build();
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CASTING, false);
        entityData.define(CAST_STARTED_AT, 0);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new SwimGoal(this));
        goalSelector.addGoal(6, new LookAtGoal(this, PlayerEntity.class, 8));
        goalSelector.addGoal(7, new LookRandomlyGoal(this));
    }

    /** World time keeps observers aligned even when they begin tracking mid-cast. */
    public float castPhase(float age) {
        if (!entityData.get(CASTING)) return CAST_DURATION_TICKS;
        // Subtract in int space so a world-time wrap does not interrupt a short cast.
        int elapsed = (int)level.getGameTime() - entityData.get(CAST_STARTED_AT);
        return MathHelper.clamp(elapsed + MathHelper.clamp(age - tickCount, 0, 1), 0, CAST_DURATION_TICKS);
    }

    @Override public void tick() {
        super.tick();
        if (level.isClientSide || !isAlive()) return;
        long now = level.getGameTime();
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

    private ServerPlayerEntity auraOwner() {
        if (!(level instanceof ServerWorld) || CompanionBrain.activeEntry(this) == null) return null;
        ServerPlayerEntity owner = getServer().getPlayerList().getPlayer(CompanionBrain.owner(this));
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level == level
            && distanceToSqr(owner) <= AURA_RADIUS * AURA_RADIUS ? owner : null;
    }

    private void applyAura() {
        // Recheck at impact: leaving, recalling or changing dimensions must cancel the buff.
        ServerPlayerEntity owner = auraOwner();
        if (owner == null) return;
        grantAura(owner);
        for (MobEntity soul : level.getEntitiesOfClass(MobEntity.class, owner.getBoundingBox().inflate(AURA_RADIUS),
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
        recipient.addEffect(new EffectInstance(Effects.DAMAGE_BOOST, AURA_DURATION_TICKS, 0, true, false, true));
        recipient.addEffect(new EffectInstance(Effects.REGENERATION, AURA_DURATION_TICKS, 0, true, false, true));
    }

    @Override public boolean canAttack(LivingEntity target) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public IPacket<?> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
