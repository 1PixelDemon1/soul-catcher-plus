// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.data.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.PlayerEntity;

public final class CompanionBrain {
    public static final String OWNER = "scp_owner", ENTRY = "scp_entry", MODE = "scp_mode";
    private static final int AGGRESSIVE_RANGE = 16, COMBAT_RADIUS = 40, HARD_TELEPORT_RADIUS = 64;
    private static final int FOLLOW_DISTANCE = 4, STUCK_TICKS = 100, TELEPORT_RETRY_TICKS = 20;
    private static final int TELEPORT_ATTEMPTS = 12, MAX_TELEPORT_RADIUS = 16;
    // Server-thread-only transient state; values hold positions, never a strong reference to the mob.
    private static final Map<MobEntity, ReturnProgress> RETURNS = new WeakHashMap<>();
    public enum Mode { FOLLOW, WAIT, GUARD, AGGRESSIVE }
    public static boolean isSoul(Entity entity) { return entity.getPersistentData().hasUUID(OWNER); }
    public static UUID owner(Entity entity) { return entity.getPersistentData().getUUID(OWNER); }
    /** Roots own the active UUID; minions validate their separately persisted root UUID. */
    public static SoulEntry activeEntry(MobEntity mob) {
        if (!isSoul(mob) || mob.getServer() == null || !mob.getPersistentData().hasUUID(ENTRY)) return null;
        SoulEntry entry = SoulLedger.get(mob.getServer()).player(owner(mob)).entries.get(mob.getPersistentData().getUUID(ENTRY));
        UUID root = SoulMinions.isMinion(mob) ? mob.getPersistentData().getUUID(SoulMinions.ROOT) : mob.getUUID();
        return entry != null && root.equals(entry.activeEntity) ? entry : null;
    }
    public static Mode mode(MobEntity mob) {
        try { return Mode.valueOf(mob.getPersistentData().getString(MODE)); }
        catch (IllegalArgumentException e) { return Mode.FOLLOW; }
    }
    public static boolean allied(MobEntity mob, Entity other) {
        UUID owner = owner(mob);
        if (other.getUUID().equals(owner)) return true;
        if (isSoul(other) && owner(other).equals(owner)) return true;
        if (owner.equals(SoulMinions.ownerUuid(other))) return true;
        ServerPlayerEntity player = mob.getServer() == null ? null : mob.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.isAlliedTo(other);
    }
    public static boolean canAttack(MobEntity mob, LivingEntity target) {
        return target != mob && target.isAlive() && !allied(mob, target)
            && !(target instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))
            && mob.canAttack(target);
    }
    public static void installStandardGoals(MobEntity mob) {
        resetReturn(mob);
        GoalSelector targets = mob.targetSelector;
        GoalSelector goals = mob.goalSelector;
        if (targets == null || goals == null) throw new IllegalStateException("Missing mob goal selectors");
        LegacyGoals.clear(targets);
        LegacyGoals.all(goals)
            .filter(goal -> goal instanceof HoldPositionGoal || goal instanceof FollowOwnerGoal)
            .collect(com.google.common.collect.ImmutableList.toImmutableList()).forEach(goals::removeGoal);
        goals.addGoal(0, new HoldPositionGoal(mob));
        goals.addGoal(0, new FollowOwnerGoal(mob, true));
        goals.addGoal(2, new FollowOwnerGoal(mob, false));
    }
    public static void tick(MobEntity mob) {
        if (mob.level.isClientSide || SoulMinions.isMinion(mob) || mob.tickCount % 5 != 0) return;
        SoulEntry entry = activeEntry(mob);
        if (entry == null) { resetReturn(mob); mob.remove(); return; }
        SoulBossBars.suppress(mob);
        ServerPlayerEntity player = mob.getServer().getPlayerList().getPlayer(owner(mob));
        // An absent owner may simply be logging in after saved entities load.
        if (player == null) { resetReturn(mob); return; }
        if (player.level != mob.level) {
            resetReturn(mob); SoulService.recall(mob.getServer(), entry); return;
        }
        if (mob.tickCount % 20 == 0) SoulNames.update(mob, player);
        if (!SoulAdapters.get(mob).usesStandardBrain(mob)) {
            resetReturn(mob);
            mob.setTarget(null);
            SoulAdapters.get(mob).tick(mob, player);
            mob.setPersistenceRequired();
            return;
        }
        if (mob.getTarget() != null && !canAttack(mob, mob.getTarget())) mob.setTarget(null);
        Mode mode = mode(mob);
        boolean returning = updateReturn(mob, player, mode);
        if (!returning) updateCombat(mob, player, mode);
        SoulAdapters.get(mob).tick(mob, player);
        if (returning) mob.setTarget(null);
        mob.setPersistenceRequired();
    }
    private static boolean followsOwner(Mode mode) { return mode == Mode.FOLLOW || mode == Mode.AGGRESSIVE; }
    static void resetReturn(MobEntity mob) { RETURNS.remove(mob); }

    /** This watchdog runs even if a native goal prevents our return goal from starting. */
    private static boolean updateReturn(MobEntity mob, ServerPlayerEntity player, Mode mode) {
        double distance = mob.distanceToSqr(player);
        if (!followsOwner(mode) || distance <= FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            resetReturn(mob);
            return false;
        }
        ReturnProgress progress = RETURNS.get(mob);
        long now = mob.level.getGameTime();
        if (progress == null) {
            if (distance <= COMBAT_RADIUS * COMBAT_RADIUS) return false;
            progress = new ReturnProgress(mob.position(), now);
            RETURNS.put(mob, progress);
            mob.getNavigation().stop(); // Abandon the old attack path only when return begins.
        }
        mob.setTarget(null);
        boolean stalled = progress.stalled(mob.position(), player.position(), now);
        if ((distance > HARD_TELEPORT_RADIUS * HARD_TELEPORT_RADIUS || stalled) && now >= progress.nextTeleportAt) {
            progress.nextTeleportAt = now + TELEPORT_RETRY_TICKS;
            teleportToOwner(mob, player);
        }
        return true;
    }
    private static final class ReturnProgress {
        private Vector3d progressAnchor;
        private long lastProgressAt, nextTeleportAt;

        ReturnProgress(Vector3d position, long now) { progressAnchor = position; lastProgressAt = now; }

        boolean stalled(Vector3d position, Vector3d owner, long now) {
            // Keep the last credited point: shuttling over the same ground must not reset the timer.
            // Compare against the same current owner position so owner movement alone cannot count as progress.
            if (progressAnchor.distanceTo(owner) - position.distanceTo(owner) >= 1) {
                progressAnchor = position;
                lastProgressAt = now;
            }
            return now - lastProgressAt >= STUCK_TICKS;
        }
    }
    private static void updateCombat(MobEntity mob, ServerPlayerEntity player, Mode mode) {
        if (followsOwner(mode) && mob.getTarget() != null && !insideCombatArea(mob.getTarget(), player))
            mob.setTarget(null);
        if (mode == Mode.WAIT) mob.setTarget(null);
        else if (mode == Mode.GUARD) {
            BlockPos guard = BlockPos.of(mob.getPersistentData().getLong("scp_guard"));
            if (mob.getTarget() != null && mob.getTarget().distanceToSqr(net.minecraft.util.math.vector.Vector3d.atCenterOf(guard)) > 16 * 16)
                mob.setTarget(null);
            if (mob.getTarget() == null) findEnemy(mob, guard);
        } else if (mode == Mode.AGGRESSIVE) {
            if (mob.getTarget() == null) findHostile(mob, player);
        } else if (mob.getTarget() == null) {
            LivingEntity attacker = player.getLastHurtByMob();
            LivingEntity victim = player.getLastHurtMob();
            if (attacker != null && player.tickCount - player.getLastHurtByMobTimestamp() < 200
                    && mob.distanceToSqr(attacker) < 32 * 32 && insideCombatArea(attacker, player)
                    && canAttack(mob, attacker)) mob.setTarget(attacker);
            else if (victim != null && player.tickCount - player.getLastHurtMobTimestamp() < 200
                    && mob.distanceToSqr(victim) < 32 * 32 && insideCombatArea(victim, player)
                    && canAttack(mob, victim)) mob.setTarget(victim);
        }
    }
    private static boolean insideCombatArea(LivingEntity target, ServerPlayerEntity player) {
        return target.distanceToSqr(player) <= COMBAT_RADIUS * COMBAT_RADIUS;
    }
    /** Try loaded positions near the owner without recreating the mob or changing its orders. */
    public static boolean teleportToOwner(MobEntity mob, ServerPlayerEntity player) {
        if (mob.level.isClientSide || mob.level != player.level) return false;
        // Large companions need clearance from the owner; keep every candidate inside the follow leash.
        int radius = Math.min(MAX_TELEPORT_RADIUS, Math.max(4, (int)Math.ceil(mob.getBbWidth() / 2) + 2));
        for (int i = 0; i < TELEPORT_ATTEMPTS; i++) {
            BlockPos pos = player.blockPosition().offset(mob.getRandom().nextInt(radius * 2 + 1) - radius,
                0, mob.getRandom().nextInt(radius * 2 + 1) - radius);
            var destination = mob.getBoundingBox().move(
                pos.getX() + 0.5 - mob.getX(), pos.getY() - mob.getY(), pos.getZ() + 0.5 - mob.getZ());
            if (mob.level.hasChunksAt(new BlockPos(destination.minX, destination.minY - 1, destination.minZ),
                        new BlockPos(destination.maxX, destination.maxY, destination.maxZ))
                    && mob.level.getBlockState(pos.below()).isSolidRender(mob.level, pos.below())
                    && mob.level.getWorldBorder().isWithinBounds(destination)
                    && !destination.intersects(player.getBoundingBox())
                    && !mob.level.containsAnyLiquid(destination) && mob.level.noCollision(mob, destination)) {
                mob.stopRiding();
                if (mob.isPassenger()) return false; // A mod may veto dismounting through Forge.
                mob.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                mob.setDeltaMovement(Vector3d.ZERO);
                mob.fallDistance = 0;
                resetReturn(mob);
                return true;
            }
        }
        return false;
    }
    private static void findEnemy(MobEntity mob, BlockPos guard) {
        mob.level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12),
            e -> e instanceof IMob && canAttack(mob, e)
                && e.distanceToSqr(net.minecraft.util.math.vector.Vector3d.atCenterOf(guard)) < 16 * 16)
            .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);
    }
    private static void findHostile(MobEntity mob, ServerPlayerEntity owner) {
        mob.level.getEntitiesOfClass(MobEntity.class, mob.getBoundingBox().inflate(AGGRESSIVE_RANGE),
            target -> canAttack(mob, target) && mob.distanceToSqr(target) <= AGGRESSIVE_RANGE * AGGRESSIVE_RANGE
                && insideCombatArea(target, owner)
                && mob.getSensing().canSee(target) && SoulAdapters.get(target).isHostile(target, owner))
            .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);
    }
    private static final class HoldPositionGoal extends Goal {
        private final MobEntity mob;
        HoldPositionGoal(MobEntity mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        public boolean canUse() { return mode(mob) == Mode.WAIT; }
        public void start() { mob.getNavigation().stop(); mob.setTarget(null); }
        public void tick() { mob.getNavigation().stop(); }
    }
    private static final class FollowOwnerGoal extends Goal {
        private final MobEntity mob;
        private ServerPlayerEntity player;
        private int ticksUntilRepath;
        private final boolean returningOnly;
        FollowOwnerGoal(MobEntity mob, boolean returningOnly) {
            this.mob = mob; this.returningOnly = returningOnly;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        public boolean canUse() {
            player = mob.getServer().getPlayerList().getPlayer(owner(mob));
            if (returningOnly) return player != null && followsOwner(mode(mob)) && RETURNS.containsKey(mob);
            if (RETURNS.containsKey(mob)) return false;
            return player != null && mob.getTarget() == null && mode(mob) != Mode.WAIT
                && (mode(mob) == Mode.GUARD || mob.distanceToSqr(player) > FOLLOW_DISTANCE * FOLLOW_DISTANCE);
        }
        public boolean canContinueToUse() { return canUse(); }
        public void start() { ticksUntilRepath = 0; }
        public void stop() { mob.getNavigation().stop(); }
        public void tick() {
            if (returningOnly) mob.setTarget(null);
            BlockPos destination = mode(mob) == Mode.GUARD
                ? BlockPos.of(mob.getPersistentData().getLong("scp_guard")) : player.blockPosition();
            // Goals can tick only on odd mob ticks, so a global % 10 check can miss every update.
            if (--ticksUntilRepath <= 0) {
                ticksUntilRepath = 10;
                mob.getNavigation().moveTo(destination.getX() + 0.5,
                    destination.getY(), destination.getZ() + 0.5, 1.1);
            }
        }
    }
    private CompanionBrain() {}
}
