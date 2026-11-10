// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persisted ownership prevents orphaned breath from hurting its former owner's party. */
@Mixin(value = AreaEffectCloudEntity.class, remap = false)
public abstract class CompanionDragonBreathMixin {
    @Unique private MobEntity soulcatcherplus$caster;

    @Inject(method = {"tick", "func_70071_h_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$validateBreath(CallbackInfo callback) {
        AreaEffectCloudEntity cloud = (AreaEffectCloudEntity)(Object)this;
        soulcatcherplus$caster = null;
        if (cloud.level.isClientSide || !cloud.getPersistentData().hasUUID(VanillaBossEvents.BREATH_ROOT)) return;
        Entity source = ((ServerWorld)cloud.level).getEntity(cloud.getPersistentData().getUUID(VanillaBossEvents.BREATH_ROOT));
        if (!(source instanceof MobEntity mob) || !mob.isAlive() || !VanillaBossEvents.isSoulBoss(mob)
                || CompanionBrain.activeEntry(mob) == null) {
            cloud.remove();
            callback.cancel();
        } else soulcatcherplus$caster = mob;
    }

    @Group(name = "soulcatcherplus$breathTargets", min = 1, max = 1)
    @Redirect(method = {"tick", "func_70071_h_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;isAffectedByPotions()Z"), remap = false, require = 0)
    private boolean soulcatcherplus$targetsOfficial(LivingEntity target) { return soulcatcherplus$eligible(target); }

    @Group(name = "soulcatcherplus$breathTargets", min = 1, max = 1)
    @Redirect(method = {"tick", "func_70071_h_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/entity/LivingEntity;func_184603_cC()Z"), remap = false, require = 0)
    private boolean soulcatcherplus$targetsSrg(LivingEntity target) { return soulcatcherplus$eligible(target); }

    @Unique private boolean soulcatcherplus$eligible(LivingEntity target) {
        return target.isAffectedByPotions() && (soulcatcherplus$caster == null
            || target != soulcatcherplus$caster && !CompanionBrain.allied(soulcatcherplus$caster, target));
    }

    @Group(name = "soulcatcherplus$breathDamage", min = 1, max = 1)
    @Redirect(method = {"tick", "func_70071_h_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/potion/Effect;applyInstantenousEffect(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/LivingEntity;ID)V"),
        remap = false, require = 0)
    private void soulcatcherplus$damageOfficial(Effect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        soulcatcherplus$damage(effect, source, owner, target, amplifier, factor);
    }

    @Group(name = "soulcatcherplus$breathDamage", min = 1, max = 1)
    @Redirect(method = {"tick", "func_70071_h_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/potion/Effect;func_180793_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/LivingEntity;ID)V"),
        remap = false, require = 0)
    private void soulcatcherplus$damageSrg(Effect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        soulcatcherplus$damage(effect, source, owner, target, amplifier, factor);
    }

    @Unique private void soulcatcherplus$damage(Effect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        if (soulcatcherplus$caster != null && effect == Effects.HARM) {
            // Same strength as vanilla Instant Damage, without healing undead enemies.
            int damage = (int)(factor * (6 << amplifier) + 0.5);
            target.hurt(DamageSource.indirectMagic(source, soulcatcherplus$caster), damage);
        } else effect.applyInstantenousEffect(source, owner, target, amplifier, factor);
    }
}
