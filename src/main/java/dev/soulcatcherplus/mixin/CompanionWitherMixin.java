// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WitherBoss.class, remap = false)
public abstract class CompanionWitherMixin {
    @Shadow(remap = false) @Final private int[] nextHeadUpdate;
    @Shadow(remap = false) @Final private int[] idleHeadUpdates;
    @Shadow(remap = false) private int destroyBlocksTick;
    @Shadow(remap = false) @Final private ServerBossEvent bossEvent;
    @Shadow(remap = false)
    private void performRangedAttack(int head, LivingEntity target) { throw new AssertionError(); }

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$orderedHeads(net.minecraft.server.level.ServerLevel level, CallbackInfo callback) {
        WitherBoss wither = (WitherBoss)(Object)this;
        if (!VanillaBossEvents.isSoulBoss(wither)) return;
        wither.setInvulnerableTicks(0);
        destroyBlocksTick = 0;
        ServerPlayer owner = wither.level().getServer().getPlayerList().getPlayer(CompanionBrain.owner(wither));
        LivingEntity target = wither.getTarget();
        if (owner == null || CompanionBrain.mode(wither) == CompanionBrain.Mode.WAIT
                || target != null && !CompanionBrain.canAttack(wither, target)) {
            wither.setTarget(null);
            target = null;
        }
        for (int head = 0; head < 3; head++) wither.setAlternativeTarget(head, target == null ? 0 : target.getId());
        for (int head = 1; head < 3; head++) {
            idleHeadUpdates[head - 1] = 0;
            if (target != null && wither.tickCount >= nextHeadUpdate[head - 1]
                    && wither.distanceToSqr(target) <= 30 * 30 && wither.hasLineOfSight(target)) {
                performRangedAttack(head + 1, target);
                nextHeadUpdate[head - 1] = wither.tickCount + 40 + wither.getRandom().nextInt(20);
            }
        }
        if (target == null) {
            wither.getNavigation().stop();
            if (owner == null || CompanionBrain.mode(wither) == CompanionBrain.Mode.WAIT) wither.setDeltaMovement(Vec3.ZERO);
            else {
                Vec3 destination = CompanionBrain.mode(wither) == CompanionBrain.Mode.GUARD
                    ? Vec3.atCenterOf(BlockPos.of(wither.getPersistentData().getLongOr("scp_guard", 0L))).add(0, 3, 0)
                    : owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(5)).add(0, 3, 0);
                if (CompanionBrain.isReturning(wither)) destination = owner.position().add(0, 2, 0);
                Vec3 offset = destination.subtract(wither.position());
                Vec3 desired = offset.lengthSqr() < 1 ? Vec3.ZERO : offset.normalize().scale(Math.min(0.6, offset.length() * 0.16));
                wither.setDeltaMovement(wither.getDeltaMovement().scale(0.7).add(desired.scale(0.3)));
            }
        }
        if (wither.tickCount % 20 == 0) wither.heal(1);
        bossEvent.setProgress(wither.getHealth() / wither.getMaxHealth());
        callback.cancel();
    }
}
