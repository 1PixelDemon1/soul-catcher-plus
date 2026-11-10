// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerBossInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WitherEntity.class, remap = false)
public abstract class CompanionWitherMixin {
    @Shadow(aliases = "field_82223_h", remap = false) @Final private int[] nextHeadUpdate;
    @Shadow(aliases = "field_82224_i", remap = false) @Final private int[] idleHeadUpdates;
    @Shadow(aliases = "field_82222_j", remap = false) private int destroyBlocksTick;
    @Shadow(aliases = "field_184744_bE", remap = false) @Final private ServerBossInfo bossEvent;
    @Shadow(aliases = "func_82216_a", remap = false)
    private void performRangedAttack(int head, LivingEntity target) { throw new AssertionError(); }

    @Inject(method = {"customServerAiStep", "func_70619_bc"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$orderedHeads(CallbackInfo callback) {
        WitherEntity wither = (WitherEntity)(Object)this;
        if (!VanillaBossEvents.isSoulBoss(wither)) return;
        wither.setInvulnerableTicks(0);
        destroyBlocksTick = 0;
        ServerPlayerEntity owner = wither.getServer().getPlayerList().getPlayer(CompanionBrain.owner(wither));
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
                    && wither.distanceToSqr(target) <= 30 * 30 && wither.canSee(target)) {
                performRangedAttack(head + 1, target);
                nextHeadUpdate[head - 1] = wither.tickCount + 40 + wither.getRandom().nextInt(20);
            }
        }
        if (target == null) {
            wither.getNavigation().stop();
            if (owner == null || CompanionBrain.mode(wither) == CompanionBrain.Mode.WAIT) wither.setDeltaMovement(Vector3d.ZERO);
            else {
                Vector3d destination = CompanionBrain.mode(wither) == CompanionBrain.Mode.GUARD
                    ? Vector3d.atCenterOf(BlockPos.of(wither.getPersistentData().getLong("scp_guard"))).add(0, 3, 0)
                    : owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(5)).add(0, 3, 0);
                if (CompanionBrain.isReturning(wither)) destination = owner.position().add(0, 2, 0);
                Vector3d offset = destination.subtract(wither.position());
                Vector3d desired = offset.lengthSqr() < 1 ? Vector3d.ZERO : offset.normalize().scale(Math.min(0.6, offset.length() * 0.16));
                wither.setDeltaMovement(wither.getDeltaMovement().scale(0.7).add(desired.scale(0.3)));
            }
        }
        if (wither.tickCount % 20 == 0) wither.heal(1);
        bossEvent.setPercent(wither.getHealth() / wither.getMaxHealth());
        callback.cancel();
    }
}
