// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Per-dragon flight state. Native aiStep still moves/animates every multipart hitbox. */
public final class SoulDragonPhase extends AbstractDragonPhaseInstance {
    private static final int FIREBALL_INTERVAL = 80, ATTACK_CYCLE = 160, ATTACK_RUN_TICKS = 70;
    private static final double ORBIT_RADIUS = 10, ORBIT_HEIGHT = 7, MAX_OWNER_DISTANCE = 32;
    private Vec3 destination;
    private int nextFireball;

    public SoulDragonPhase(EnderDragon dragon) {
        super(dragon);
        nextFireball = dragon.tickCount + 20;
    }

    @Override public void doServerTick() {
        ServerPlayer owner = dragon.getServer().getPlayerList().getPlayer(CompanionBrain.owner(dragon));
        CompanionBrain.Mode mode = CompanionBrain.mode(dragon);
        if (owner == null || owner.level != dragon.level || mode == CompanionBrain.Mode.WAIT) {
            destination = null;
            dragon.setDeltaMovement(Vec3.ZERO);
            return;
        }
        LivingEntity target = dragon.getTarget();
        if (target != null && !CompanionBrain.canAttack(dragon, target)) {
            dragon.setTarget(null);
            target = null;
        }
        Vec3 anchor = mode == CompanionBrain.Mode.GUARD
            ? Vec3.atCenterOf(BlockPos.of(dragon.getPersistentData().getLong("scp_guard"))) : owner.position();
        double angle = dragon.tickCount * 0.02 + (dragon.getUUID().hashCode() & 255);
        Vec3 orbit = new Vec3(Math.cos(angle) * ORBIT_RADIUS, ORBIT_HEIGHT, Math.sin(angle) * ORBIT_RADIUS);
        if (CompanionBrain.isReturning(dragon)) destination = owner.position().add(0, 2, 0);
        else if (target == null) destination = anchor.add(orbit);
        else destination = dragon.tickCount % ATTACK_CYCLE < ATTACK_RUN_TICKS
            ? target.position().add(0, 2, 0) : target.position().add(orbit);
        // An attack orbit must not continually cross the shared follow leash.
        if (mode != CompanionBrain.Mode.GUARD) {
            Vec3 offset = destination.subtract(owner.position());
            if (offset.lengthSqr() > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE)
                destination = owner.position().add(offset.normalize().scale(MAX_OWNER_DISTANCE));
        }
        if (target != null && dragon.tickCount >= nextFireball && dragon.distanceToSqr(target) <= 32 * 32 && dragon.hasLineOfSight(target)) {
            Vec3 muzzle = new Vec3(dragon.head.getX(), dragon.head.getY(0.5) + 0.5, dragon.head.getZ());
            Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(muzzle);
            DragonFireball fireball = new DragonFireball(dragon.level, dragon, aim.x, aim.y, aim.z);
            fireball.moveTo(muzzle.x, muzzle.y, muzzle.z, 0, 0);
            if (dragon.level.addFreshEntity(fireball)) {
                nextFireball = dragon.tickCount + FIREBALL_INTERVAL;
                if (!dragon.isSilent()) dragon.level.levelEvent(null, 1017, dragon.blockPosition(), 0);
            }
        }
    }

    @Override public Vec3 getFlyTargetLocation() { return destination; }
    @Override public float getTurnSpeed() { return super.getTurnSpeed() * 2; }
    @Override public EnderDragonPhase<?> getPhase() { return EnderDragonPhase.HOVERING; }
}
