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
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Persisted ownership prevents orphaned breath from hurting its former owner's party. */
@Mixin(value = AreaEffectCloud.class, remap = false)
public abstract class CompanionDragonBreathMixin {
    @Unique private Mob soulcatcherplus$caster;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void soulcatcherplus$validateBreath(CallbackInfo callback) {
        AreaEffectCloud cloud = (AreaEffectCloud)(Object)this;
        soulcatcherplus$caster = null;
        if (cloud.level().isClientSide() || !dev.soulcatcherplus.data.SoulNbt.hasUuid(cloud.getPersistentData(), VanillaBossEvents.BREATH_ROOT)) return;
        Entity source = ((ServerLevel)cloud.level()).getEntity(dev.soulcatcherplus.data.SoulNbt.uuid(cloud.getPersistentData(), VanillaBossEvents.BREATH_ROOT));
        if (!(source instanceof Mob mob) || !mob.isAlive() || !VanillaBossEvents.isSoulBoss(mob)
                || CompanionBrain.activeEntry(mob) == null) {
            cloud.discard();
            callback.cancel();
        } else soulcatcherplus$caster = mob;
    }

    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;isAffectedByPotions()Z"), remap = false, require = 1)
    private boolean soulcatcherplus$targetsOfficial(LivingEntity target) { return soulcatcherplus$eligible(target); }

    @Unique private boolean soulcatcherplus$eligible(LivingEntity target) {
        return target.isAffectedByPotions() && (soulcatcherplus$caster == null
            || target != soulcatcherplus$caster && !CompanionBrain.allied(soulcatcherplus$caster, target));
    }

    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/effect/MobEffect;applyInstantaneousEffect(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"),
        remap = false, require = 1)
    private void soulcatcherplus$damageOfficial(MobEffect effect, ServerLevel level, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        soulcatcherplus$damage(effect, level, source, owner, target, amplifier, factor);
    }

    @Unique private void soulcatcherplus$damage(MobEffect effect, ServerLevel level, Entity source, Entity owner, LivingEntity target, int amplifier, double factor) {
        if (soulcatcherplus$caster != null && effect == MobEffects.INSTANT_DAMAGE.value()) {
            // Same strength as vanilla Instant Damage, without healing undead enemies.
            int damage = (int)(factor * (6 << amplifier) + 0.5);
            target.hurtServer(level, target.damageSources().indirectMagic(source, soulcatcherplus$caster), damage);
        } else effect.applyInstantaneousEffect(level, source, owner, target, amplifier, factor);
    }
}
