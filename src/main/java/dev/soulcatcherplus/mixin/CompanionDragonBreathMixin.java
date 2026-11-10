// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.companion.CompanionBrain;
import dev.soulcatcherplus.event.VanillaBossEvents;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persisted ownership prevents orphaned breath from hurting its former owner's party. */
@Mixin(value = AreaEffectCloud.class, remap = false)
public abstract class CompanionDragonBreathMixin {
    @Unique private Mob soulcatcherplus$caster;

    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$validateBreath(CallbackInfo callback) {
        AreaEffectCloud cloud = (AreaEffectCloud)(Object)this;
        soulcatcherplus$caster = null;
        if (cloud.level().isClientSide || !cloud.getPersistentData().hasUUID(VanillaBossEvents.BREATH_ROOT)) return;
        Entity source = ((ServerLevel)cloud.level()).getEntity(cloud.getPersistentData().getUUID(VanillaBossEvents.BREATH_ROOT));
        if (!(source instanceof Mob mob) || !mob.isAlive() || !VanillaBossEvents.isSoulBoss(mob)
                || CompanionBrain.activeEntry(mob) == null) {
            cloud.discard();
            callback.cancel();
        } else soulcatcherplus$caster = mob;
    }

    @Group(name = "soulcatcherplus$breathTargets", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"), remap = false, require = 0)
    private boolean soulcatcherplus$targetsOfficial(LivingEntity target) { return soulcatcherplus$eligible(target); }

    @Group(name = "soulcatcherplus$breathTargets", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;m_5801_()Z"), remap = false, require = 0)
    private boolean soulcatcherplus$targetsSrg(LivingEntity target) { return soulcatcherplus$eligible(target); }

    @Unique private boolean soulcatcherplus$eligible(LivingEntity target) {
        return target.isAffectedByPotions() && (soulcatcherplus$caster == null
            || target != soulcatcherplus$caster && !CompanionBrain.allied(soulcatcherplus$caster, target));
    }

    @Group(name = "soulcatcherplus$breathDamage", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"),
        remap = false, require = 0)
    private void soulcatcherplus$damageOfficial(MobEffect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        soulcatcherplus$damage(effect, source, owner, target, amplifier, factor);
    }

    @Group(name = "soulcatcherplus$breathDamage", min = 1, max = 1)
    @Redirect(method = {"tick", "m_8119_"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/effect/MobEffect;m_19461_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"),
        remap = false, require = 0)
    private void soulcatcherplus$damageSrg(MobEffect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        soulcatcherplus$damage(effect, source, owner, target, amplifier, factor);
    }

    @Unique private void soulcatcherplus$damage(MobEffect effect, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        if (soulcatcherplus$caster != null && effect == MobEffects.HARM) {
            // Same strength as vanilla Instant Damage, without healing undead enemies.
            int damage = (int)(factor * (6 << amplifier) + 0.5);
            target.hurt(target.damageSources().indirectMagic(source, soulcatcherplus$caster), damage);
        } else effect.applyInstantenousEffect(source, owner, target, amplifier, factor);
    }
}
