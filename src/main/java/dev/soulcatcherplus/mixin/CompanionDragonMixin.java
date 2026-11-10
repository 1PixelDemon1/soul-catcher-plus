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
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remove encounter-only side effects while retaining the native dragon's body and animations. */
@Mixin(value = EnderDragon.class, remap = false)
public abstract class CompanionDragonMixin extends Mob implements DragonSoulAdapter.Control {
    @Shadow(remap = false) private EnderDragonFight dragonFight;
    @Shadow(remap = false) @Final private EnderDragonPart neck;

    protected CompanionDragonMixin(EntityType<? extends Mob> type, Level world) { super(type, world); }

    @Override public void soulcatcherplus$detachFight() { dragonFight = null; }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "aiStep", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;getDragonFight()Lnet/minecraft/world/level/dimension/end/EnderDragonFight;"), remap = false)
    private EnderDragonFight soulcatcherplus$ignoreEncounter(net.minecraft.server.level.ServerLevel level) {
        return VanillaBossEvents.isSoulBoss(this) ? null : level.getDragonFight();
    }

    @Inject(method = "checkCrystals", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ignoreCrystals(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        ((EnderDragon)(Object)this).nearestCrystal = null;
        callback.cancel();
    }

    @Inject(method = "checkWalls", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$preserveBlocks(net.minecraft.server.level.ServerLevel level, AABB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(false);
    }

    @ModifyVariable(method = {"knockBack(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V",
        "hurt(Lnet/minecraft/server/level/ServerLevel;Ljava/util/List;)V"}, at = @At("HEAD"), argsOnly = true, remap = false)
    private List<Entity> soulcatcherplus$combatContacts(List<Entity> entities) {
        if (!VanillaBossEvents.isSoulBoss(this)) return entities;
        return entities.stream().filter(entity -> entity == getTarget() && entity instanceof net.minecraft.world.entity.LivingEntity
            && CompanionBrain.mode(this) != CompanionBrain.Mode.WAIT
            && CompanionBrain.canAttack(this, (net.minecraft.world.entity.LivingEntity)entity)).collect(Collectors.toList());
    }

    @Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ordinaryDamage(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        if (VanillaBossEvents.isSoulBoss(this)) callback.setReturnValue(super.hurtServer(level, source, amount));
    }

    @Inject(method = "hurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/boss/enderdragon/EnderDragonPart;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$partDamage(net.minecraft.server.level.ServerLevel level, EnderDragonPart part, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (part != ((EnderDragon)(Object)this).head && part != neck) amount = amount / 4 + Math.min(amount, 1);
        callback.setReturnValue(super.hurtServer(level, source, amount));
    }

    @Inject(method = "handleKillingBlow", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$ordinaryDeath(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        super.handleKillingBlow();
        callback.cancel();
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$companionDeath(CallbackInfo callback) {
        if (!VanillaBossEvents.isSoulBoss(this)) return;
        if (!level().isClientSide()) discard();
        callback.cancel();
    }
}
