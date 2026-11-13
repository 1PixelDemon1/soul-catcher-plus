// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.Phase;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

/** Per-dragon flight state. Native aiStep still moves/animates every multipart hitbox. */
public final class SoulDragonPhase extends Phase {
    private static final int FIREBALL_INTERVAL = 80, ATTACK_CYCLE = 160, ATTACK_RUN_TICKS = 70;
    private static final double ORBIT_RADIUS = 10, ORBIT_HEIGHT = 7, MAX_OWNER_DISTANCE = 32;
    private Vector3d destination;
    private int nextFireball;

    public SoulDragonPhase(EnderDragonEntity dragon) {
        super(dragon);
        nextFireball = dragon.tickCount + 20;
    }

    @Override public void doServerTick() {
        ServerPlayerEntity owner = dragon.getServer().getPlayerList().getPlayer(CompanionBrain.owner(dragon));
        CompanionBrain.Mode mode = CompanionBrain.mode(dragon);
        if (owner == null || owner.level != dragon.level || mode == CompanionBrain.Mode.WAIT) {
            destination = null;
            dragon.setDeltaMovement(Vector3d.ZERO);
            return;
        }
        LivingEntity target = dragon.getTarget();
        if (target != null && !CompanionBrain.canAttack(dragon, target)) {
            dragon.setTarget(null);
            target = null;
        }
        Vector3d anchor = mode == CompanionBrain.Mode.GUARD
            ? Vector3d.atCenterOf(BlockPos.of(dragon.getPersistentData().getLong("scp_guard"))) : owner.position();
        double angle = dragon.tickCount * 0.02 + (dragon.getUUID().hashCode() & 255);
        Vector3d orbit = new Vector3d(Math.cos(angle) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(angle) * ORBIT_RADIUS);
        if (CompanionBrain.isReturning(dragon)) destination = owner.position().add(0, 2, 0);
        else if (target == null) destination = anchor.add(orbit);
        else destination = dragon.tickCount % ATTACK_CYCLE < ATTACK_RUN_TICKS
            ? target.position().add(0, 2, 0) : target.position().add(orbit);
        // Follow keeps its compact orbit; Aggressive combat uses the shared hard leash.
        if (mode != CompanionBrain.Mode.GUARD && !(mode == CompanionBrain.Mode.AGGRESSIVE && target != null)) {
            Vector3d offset = destination.subtract(owner.position());
            if (offset.lengthSqr() > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE)
                destination = owner.position().add(offset.normalize().scale(MAX_OWNER_DISTANCE));
        }
        if (target != null && dragon.tickCount >= nextFireball && dragon.distanceToSqr(target) <= 32 * 32 && dragon.canSee(target)) {
            Vector3d muzzle = new Vector3d(dragon.head.getX(), dragon.head.getY(0.5) + 0.5, dragon.head.getZ());
            Vector3d aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(muzzle);
            DragonFireballEntity fireball = new DragonFireballEntity(dragon.level, dragon, aim.x, aim.y, aim.z);
            fireball.moveTo(muzzle.x, muzzle.y, muzzle.z, 0, 0);
            if (dragon.level.addFreshEntity(fireball)) {
                nextFireball = dragon.tickCount + FIREBALL_INTERVAL;
                if (!dragon.isSilent()) dragon.level.levelEvent(null, 1017, dragon.blockPosition(), 0);
            }
        }
    }

    @Override public Vector3d getFlyTargetLocation() { return destination; }
    @Override public float getTurnSpeed() { return super.getTurnSpeed() * 2; }
    @Override public PhaseType<?> getPhase() { return PhaseType.HOVERING; }
}
