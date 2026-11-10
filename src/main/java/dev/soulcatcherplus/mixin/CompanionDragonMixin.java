// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.DragonSoulAdapter;
import dev.soulcatcherplus.event.VanillaBossEvents;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remove encounter-only side effects while retaining the native dragon's body and animations. */
@Mixin(value = EnderDragon.class, remap = false)
public abstract class CompanionDragonMixin extends Mob implements DragonSoulAdapter.Control {
    @Shadow(aliases = "f_31073_", remap = false) private EndDragonFight dragonFight;

    protected CompanionDragonMixin(EntityType<? extends Mob> type, Level world) { super(type, world); }

    @Override public void soulcatcherplus$detachFight() { dragonFight = null; }

    @org.spongepowered.asm.mixin.injection.Group(name = "soulcatcherplus$detachEncounter", min = 1, max = 1)
    @org.spongepowered.asm.mixin.injection.Redirect(method = {"aiStep", "m_8107_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getDragonFight()Lnet/minecraft/world/level/dimension/end/EndDragonFight;"), remap = false, require = 0)
    private EndDragonFight soulcatcherplus$ignoreEncounter(net.minecraft.server.level.ServerLevel level) {
        return VanillaBossEvents.isSoulBoss(this) ? null : level.getDragonFight();
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "soulcatcherplus$detachEncounter", min = 1, max = 1)
    @org.spongepowered.asm.mixin.injection.Redirect(method = {"aiStep", "m_8107_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;m_8586_()Lnet/minecraft/world/level/dimension/end/EndDragonFight;"), remap = false, require = 0)
    private EndDragonFight soulcatcherplus$ignoreEncounterSrg(net.minecraft.server.level.ServerLevel level) {
        return VanillaBossEvents.isSoulBoss(this) ? null : level.getDragonFight();
    }

    @Inject(method = {"checkCrystals", "m_31160_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ignoreCrystals(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        ((EnderDragon)(Object)this).nearestCrystal = null;
        callback.cancel();
    }

    @Inject(method = {"checkWalls", "m_31139_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveBlocks(AABB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(false);
    }

    @ModifyVariable(method = {"knockBack(Ljava/util/List;)V", "m_31131_(Ljava/util/List;)V",
        "hurt(Ljava/util/List;)V", "m_31141_(Ljava/util/List;)V"}, at = @At("HEAD"), argsOnly = true, remap = false)
    private List<Entity> soulcatcherplus$combatContacts(List<Entity> entities) {
        if (!VanillaBossEvents.isSoulBoss(this)) return entities;
        return entities.stream().filter(entity -> entity == getTarget() && entity instanceof net.minecraft.world.entity.LivingEntity
            && CompanionBrain.mode(this) != CompanionBrain.Mode.WAIT
            && CompanionBrain.canAttack(this, (net.minecraft.world.entity.LivingEntity)entity)).collect(Collectors.toList());
    }

    @Inject(method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ordinaryDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(super.hurt(source, amount));
    }

    @Inject(method = {"hurt(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        "m_31120_(Lnet/minecraft/world/entity/boss/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$partDamage(EnderDragonPart part, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (part != ((EnderDragon)(Object)this).head) amount = amount / 4 + Math.min(amount, 1);
        callback.setReturnValue(super.hurt(source, amount));
    }

    @Inject(method = {"tickDeath", "m_6153_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$companionDeath(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (!level().isClientSide) discard();
        callback.cancel();
    }
}
