// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import dev.soulcatcherplus.api.SoulAdapters;
import dev.soulcatcherplus.data.*;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

public final class CompanionBrain {
    public static final String OWNER = "scp_owner", ENTRY = "scp_entry", MODE = "scp_mode";
    private static final int AGGRESSIVE_RANGE = 16, OWNER_LEASH = 24;
    public enum Mode { FOLLOW, WAIT, GUARD, AGGRESSIVE }
    public static boolean isSoul(Entity entity) { return entity.getPersistentData().hasUUID(OWNER); }
    public static UUID owner(Entity entity) { return entity.getPersistentData().getUUID(OWNER); }
    /** Roots own the active UUID; minions validate their separately persisted root UUID. */
    public static SoulEntry activeEntry(Mob mob) {
        if (!isSoul(mob) || mob.getServer() == null || !mob.getPersistentData().hasUUID(ENTRY)) return null;
        SoulEntry entry = SoulLedger.get(mob.getServer()).player(owner(mob)).entries.get(mob.getPersistentData().getUUID(ENTRY));
        UUID root = SoulMinions.isMinion(mob) ? mob.getPersistentData().getUUID(SoulMinions.ROOT) : mob.getUUID();
        return entry != null && root.equals(entry.activeEntity) ? entry : null;
    }
    public static Mode mode(Mob mob) {
        try { return Mode.valueOf(mob.getPersistentData().getString(MODE)); }
        catch (IllegalArgumentException e) { return Mode.FOLLOW; }
    }
    public static boolean allied(Mob mob, Entity other) {
        UUID owner = owner(mob);
        if (other.getUUID().equals(owner)) return true;
        if (isSoul(other) && owner(other).equals(owner)) return true;
        if (other instanceof OwnableEntity tame && owner.equals(tame.getOwnerUUID())) return true;
        ServerPlayer player = mob.getServer() == null ? null : mob.getServer().getPlayerList().getPlayer(owner);
        return player != null && player.isAlliedTo(other);
    }
    public static boolean canAttack(Mob mob, LivingEntity target) {
        return target != mob && target.isAlive() && !allied(mob, target)
            && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))
            && mob.canAttack(target);
    }
    public static void installStandardGoals(Mob mob) {
        GoalSelector targets = mob.targetSelector;
        GoalSelector goals = mob.goalSelector;
        if (targets == null || goals == null) throw new IllegalStateException("Missing mob goal selectors");
        targets.removeAllGoals(goal -> true);
        goals.addGoal(0, new HoldPositionGoal(mob));
        goals.addGoal(2, new FollowOwnerGoal(mob));
    }
    public static void tick(Mob mob) {
        if (mob.level().isClientSide || SoulMinions.isMinion(mob) || mob.tickCount % 5 != 0) return;
        SoulEntry entry = activeEntry(mob);
        if (entry == null) { mob.discard(); return; }
        SoulBossBars.suppress(mob);
        ServerPlayer player = mob.getServer().getPlayerList().getPlayer(owner(mob));
        if (player == null || player.level() != mob.level()) { SoulService.recall(mob.getServer(), entry); return; }
        if (mob.getTarget() != null && !canAttack(mob, mob.getTarget())) mob.setTarget(null);
        Mode mode = mode(mob);
        if (mode == Mode.WAIT) mob.setTarget(null);
        else if (mode == Mode.GUARD) {
            BlockPos guard = BlockPos.of(mob.getPersistentData().getLong("scp_guard"));
            if (mob.getTarget() != null && mob.getTarget().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(guard)) > 16 * 16)
                mob.setTarget(null);
            if (mob.getTarget() == null) findEnemy(mob, guard);
        } else if (mode == Mode.AGGRESSIVE) {
            boolean nearOwner = mob.distanceToSqr(player) <= OWNER_LEASH * OWNER_LEASH;
            if (!nearOwner || (mob.getTarget() != null && mob.getTarget().distanceToSqr(player) > OWNER_LEASH * OWNER_LEASH))
                mob.setTarget(null);
            if (nearOwner && mob.getTarget() == null) findHostile(mob, player);
        } else if (mob.getTarget() == null) {
            LivingEntity attacker = player.getLastHurtByMob();
            LivingEntity victim = player.getLastHurtMob();
            if (attacker != null && player.tickCount - player.getLastHurtByMobTimestamp() < 200
                    && mob.distanceToSqr(attacker) < 32 * 32 && canAttack(mob, attacker)) mob.setTarget(attacker);
            else if (victim != null && player.tickCount - player.getLastHurtMobTimestamp() < 200
                    && mob.distanceToSqr(victim) < 32 * 32 && canAttack(mob, victim)) mob.setTarget(victim);
        }
        SoulAdapters.get(mob).tick(mob, player);
        mob.setPersistenceRequired();
    }
    private static void findEnemy(Mob mob, BlockPos guard) {
        mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(12),
            e -> e instanceof Enemy && canAttack(mob, e)
                && e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(guard)) < 16 * 16)
            .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);
    }
    private static void findHostile(Mob mob, ServerPlayer owner) {
        mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(AGGRESSIVE_RANGE),
            target -> canAttack(mob, target) && mob.distanceToSqr(target) <= AGGRESSIVE_RANGE * AGGRESSIVE_RANGE
                && target.distanceToSqr(owner) <= AGGRESSIVE_RANGE * AGGRESSIVE_RANGE
                && mob.getSensing().hasLineOfSight(target) && SoulAdapters.get(target).isHostile(target, owner))
            .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).ifPresent(mob::setTarget);
    }
    private static final class HoldPositionGoal extends Goal {
        private final Mob mob;
        HoldPositionGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        public boolean canUse() { return mode(mob) == Mode.WAIT; }
        public void start() { mob.getNavigation().stop(); mob.setTarget(null); }
        public void tick() { mob.getNavigation().stop(); }
    }
    private static final class FollowOwnerGoal extends Goal {
        private final Mob mob;
        private ServerPlayer player;
        private int ticksUntilRepath;
        FollowOwnerGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        public boolean canUse() {
            player = mob.getServer().getPlayerList().getPlayer(owner(mob));
            return player != null && mob.getTarget() == null && mode(mob) != Mode.WAIT
                && (mode(mob) == Mode.GUARD || mob.distanceToSqr(player) > 4 * 4);
        }
        public boolean canContinueToUse() { return canUse(); }
        public void start() { ticksUntilRepath = 0; }
        public void stop() { mob.getNavigation().stop(); }
        public void tick() {
            BlockPos destination = mode(mob) == Mode.GUARD
                ? BlockPos.of(mob.getPersistentData().getLong("scp_guard")) : player.blockPosition();
            // Goals can tick only on odd mob ticks, so a global % 10 check can miss every update.
            if (--ticksUntilRepath <= 0) {
                ticksUntilRepath = adjustedTickDelay(10);
                mob.getNavigation().moveTo(destination.getX() + 0.5,
                    destination.getY(), destination.getZ() + 0.5, 1.1);
            }
            if ((mode(mob) == Mode.FOLLOW || mode(mob) == Mode.AGGRESSIVE)
                    && mob.distanceToSqr(player) > OWNER_LEASH * OWNER_LEASH) {
                for (int i = 0; i < 12; i++) {
                    BlockPos pos = player.blockPosition().offset(mob.getRandom().nextInt(9) - 4, 0, mob.getRandom().nextInt(9) - 4);
                    if (mob.level().hasChunkAt(pos) && mob.level().getBlockState(pos.below()).isSolidRender(mob.level(), pos.below())
                            && mob.level().noCollision(mob, mob.getBoundingBox().move(
                                pos.getX() + 0.5 - mob.getX(), pos.getY() - mob.getY(), pos.getZ() + 0.5 - mob.getZ()))) {
                        mob.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5); break;
                    }
                }
            }
        }
    }
    private CompanionBrain() {}
}
