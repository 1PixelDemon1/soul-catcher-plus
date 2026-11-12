// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.companion;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/** Native combat flight, with passive hovering and a stable owner stand-off. */
public final class WitherSoulMoveControl extends FlyingMoveControl {
    private static final double NEAR_DISTANCE = 3, FAR_DISTANCE = 7, FOLLOW_DISTANCE = 5;
    private static final double FOLLOW_HEIGHT = 3, RETURN_DISTANCE = 2, RETURN_HEIGHT = 2;
    private final WitherBoss wither;

    public WitherSoulMoveControl(WitherBoss wither) {
        super(wither, 10, false);
        this.wither = wither;
    }

    public static boolean holdsPosition(Mob mob) {
        return mob.getTarget() == null || CompanionBrain.mode(mob) == CompanionBrain.Mode.WAIT
            || CompanionBrain.isReturning(mob);
    }

    @Override public void tick() {
        if (holdsPosition(wither)) {
            // stop() on navigation leaves an old MOVE_TO; discard it before it steers again.
            operation = Operation.WAIT;
            wither.setSpeed(0);
            wither.setXxa(0);
            wither.setYya(0);
            wither.setZza(0);
        } else {
            super.tick();
        }
        // Native FlyingMoveControl(false) re-enables gravity when waiting for a path.
        wither.setNoGravity(true);
    }

    /** Keep the current side of the player, independently of where their camera points. */
    public static Vec3 followPosition(WitherBoss wither, ServerPlayer owner, boolean returning) {
        Vec3 offset = wither.position().subtract(owner.position()).multiply(1, 0, 1);
        double distance = offset.length();
        Vec3 direction = distance > 1.0E-4 ? offset.scale(1.0 / distance) : new Vec3(1, 0, 0);
        // This fits inside the shared four-block return-completion radius.
        if (returning) return owner.position().add(direction.scale(RETURN_DISTANCE)).add(0, RETURN_HEIGHT, 0);
        if (distance >= NEAR_DISTANCE && distance <= FAR_DISTANCE)
            return new Vec3(wither.getX(), owner.getY() + FOLLOW_HEIGHT, wither.getZ());
        return owner.position().add(direction.scale(FOLLOW_DISTANCE)).add(0, FOLLOW_HEIGHT, 0);
    }
}
