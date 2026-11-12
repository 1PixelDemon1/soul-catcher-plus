// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.WitherSoulMoveControl;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
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
public abstract class CompanionWitherMixin extends Monster {
    @Shadow(aliases = "f_31427_", remap = false) @Final private int[] nextHeadUpdate;
    @Shadow(aliases = "f_31428_", remap = false) @Final private int[] idleHeadUpdates;
    @Shadow(aliases = "f_31429_", remap = false) private int destroyBlocksTick;
    @Shadow(aliases = "f_31430_", remap = false) @Final private ServerBossEvent bossEvent;
    @Shadow(aliases = "m_31457_", remap = false)
    private void performRangedAttack(int head, LivingEntity target) { throw new AssertionError(); }

    protected CompanionWitherMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = {"customServerAiStep", "m_8024_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$orderedHeads(CallbackInfo callback) {
        WitherBoss wither = (WitherBoss)(Object)this;
        if (!VanillaBossEvents.isSoulBoss(wither)) return;
        if (!(moveControl instanceof WitherSoulMoveControl)) moveControl = new WitherSoulMoveControl(wither);
        wither.setInvulnerableTicks(0);
        destroyBlocksTick = 0;
        ServerPlayer owner = wither.getServer().getPlayerList().getPlayer(CompanionBrain.owner(wither));
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
                    ? Vec3.atCenterOf(BlockPos.of(wither.getPersistentData().getLong("scp_guard"))).add(0, 3, 0)
                    : WitherSoulMoveControl.followPosition(wither, owner, CompanionBrain.isReturning(wither));
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
