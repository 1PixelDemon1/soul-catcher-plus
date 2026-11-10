// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.companion.DragonSoulAdapter;
import dev.soulcatcherplus.event.VanillaBossEvents;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPartEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.end.DragonFightManager;
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
@Mixin(value = EnderDragonEntity.class, remap = false)
public abstract class CompanionDragonMixin extends MobEntity implements DragonSoulAdapter.Control {
    @Shadow(aliases = "field_184676_bI", remap = false) @Final @Mutable private DragonFightManager dragonFight;

    protected CompanionDragonMixin(EntityType<? extends MobEntity> type, World world) { super(type, world); }

    @Override public void soulcatcherplus$detachFight() { dragonFight = null; }

    @Inject(method = {"checkCrystals", "func_70969_j"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ignoreCrystals(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        ((EnderDragonEntity)(Object)this).nearestCrystal = null;
        callback.cancel();
    }

    @Inject(method = {"checkWalls", "func_70972_a"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveBlocks(AxisAlignedBB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(false);
    }

    @ModifyVariable(method = {"knockBack(Ljava/util/List;)V", "func_70970_a(Ljava/util/List;)V",
        "hurt(Ljava/util/List;)V", "func_70971_b(Ljava/util/List;)V"}, at = @At("HEAD"), argsOnly = true, remap = false)
    private List<Entity> soulcatcherplus$combatContacts(List<Entity> entities) {
        if (!VanillaBossEvents.isSoulBoss(this)) return entities;
        return entities.stream().filter(entity -> entity == getTarget() && entity instanceof net.minecraft.entity.LivingEntity
            && CompanionBrain.mode(this) != CompanionBrain.Mode.WAIT
            && CompanionBrain.canAttack(this, (net.minecraft.entity.LivingEntity)entity)).collect(Collectors.toList());
    }

    @Inject(method = {"hurt(Lnet/minecraft/util/DamageSource;F)Z", "func_70097_a(Lnet/minecraft/util/DamageSource;F)Z"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ordinaryDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(super.hurt(source, amount));
    }

    @Inject(method = {"hurt(Lnet/minecraft/entity/boss/dragon/EnderDragonPartEntity;Lnet/minecraft/util/DamageSource;F)Z",
        "func_213403_a(Lnet/minecraft/entity/boss/dragon/EnderDragonPartEntity;Lnet/minecraft/util/DamageSource;F)Z"},
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$partDamage(EnderDragonPartEntity part, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (part != ((EnderDragonEntity)(Object)this).head) amount = amount / 4 + Math.min(amount, 1);
        callback.setReturnValue(super.hurt(source, amount));
    }

    @Inject(method = {"tickDeath", "func_70609_aI"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$companionDeath(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (!level.isClientSide) remove();
        callback.cancel();
    }
}
