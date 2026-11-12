// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.AngerLevel;
import net.minecraft.world.entity.monster.warden.AngerManagement;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Warden.class, remap = false)
public abstract class CompanionWardenMixin extends Monster {
    @Shadow(remap = true) private AngerManagement angerManagement;
    @Shadow(aliases = "m_219451_", remap = false)
    private void syncClientAngerLevel() { throw new AssertionError(); }
    @Unique private boolean soulcatcherplus$angerInitialized;
    @Unique private static final int soulcatcherplus$INITIAL_SONIC_COOLDOWN = 200;

    protected CompanionWardenMixin(EntityType<? extends Monster> type, Level level) { super(type, level); }

    /** Vanilla reads its Brain, whereas every companion command writes Mob's ordinary target. */
    @Inject(method = {"getTarget()Lnet/minecraft/world/entity/LivingEntity;", "m_5448_()Lnet/minecraft/world/entity/LivingEntity;"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$commandTarget(CallbackInfoReturnable<LivingEntity> callback) {
        if (CompanionBrain.isSoul((Warden)(Object)this)) callback.setReturnValue(super.getTarget());
    }

    @Inject(method = {"customServerAiStep", "m_8024_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$orderedAttacks(CallbackInfo callback) {
        Warden warden = (Warden)(Object)this;
        if (!CompanionBrain.isSoul(warden)) return;
        ServerLevel level = (ServerLevel)warden.level();
        LivingEntity target = super.getTarget();
        if (CompanionBrain.mode(warden) == CompanionBrain.Mode.WAIT || CompanionBrain.isReturning(warden)
                || target != null && !CompanionBrain.canAttack(warden, target)) {
            super.setTarget(null);
            target = null;
        }
        var brain = warden.getBrain();
        LivingEntity previous = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        boolean changed = previous != target;
        if (changed) {
            // The adapter has replaced all encounter behaviors with native Sonic Boom and melee.
            brain.stopAll(level, warden);
            if (target == null) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            else {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
                SonicBoom.setCooldown(warden, soulcatcherplus$INITIAL_SONIC_COOLDOWN);
            }
        }
        if (!soulcatcherplus$angerInitialized || changed) {
            // Old captured suspects and nearby vibrations cannot choose a different victim.
            angerManagement = new AngerManagement(warden::canTargetEntity, List.of());
            if (target != null) angerManagement.increaseAnger(target, AngerLevel.ANGRY.getMinimumAnger());
            soulcatcherplus$angerInitialized = true;
        }
        brain.tick(level, warden);
        syncClientAngerLevel();
        super.customServerAiStep();
        // Skip autonomous digging, sniffing/roaring, target selection and the friendly darkness pulse.
        callback.cancel();
    }

    @Inject(method = {"canTargetEntity", "m_219385_"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void soulcatcherplus$protectAllies(Entity candidate, CallbackInfoReturnable<Boolean> callback) {
        Warden warden = (Warden)(Object)this;
        if (callback.getReturnValueZ() && CompanionBrain.isSoul(warden) && CompanionBrain.allied(warden, candidate))
            callback.setReturnValue(false);
    }

    @Inject(method = {"increaseAngerAt(Lnet/minecraft/world/entity/Entity;IZ)V", "m_219387_(Lnet/minecraft/world/entity/Entity;IZ)V"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ignoreAutonomousAnger(Entity entity, int amount, boolean sound, CallbackInfo callback) {
        if (CompanionBrain.isSoul((Warden)(Object)this)) callback.cancel();
    }

    @Inject(method = {"setAttackTarget", "m_219459_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ignoreAutonomousTarget(LivingEntity target, CallbackInfo callback) {
        if (CompanionBrain.isSoul((Warden)(Object)this)) callback.cancel();
    }
}
