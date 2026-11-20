// SPDX-License-Identifier: MIT
package dev.soulcatcherplus.mixin;

import dev.soulcatcherplus.SoulCatcherPlus;
import dev.soulcatcherplus.companion.SoulCages;
import dev.soulcatcherplus.item.SoulItem;
import dev.soulcatcherplus.network.SoulNetwork;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class SoulTotemMixin {
    @Inject(method = {"checkTotemDeathProtection", "m_21262_"}, at = @At("RETURN"), cancellable = true, remap = false)
    private void soulcatcherplus$trySoulTotem(DamageSource source, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) || !((Object)this instanceof ServerPlayer player)) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack totem = player.getItemInHand(hand);
            if (totem.getItem() != SoulCatcherPlus.SOUL_SACRIFICE_TOTEM.get()) continue;
            if (SoulCages.spendEquipment(player, SoulItem.SACRIFICE_COST).isEmpty()) return;
            player.setHealth(1);
            player.removeAllEffects();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800));
            player.awardStat(Stats.ITEM_USED.get(totem.getItem()));
            CriteriaTriggers.USED_TOTEM.trigger(player, totem);
            player.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1,
                player.getZ(), 30, .35, .5, .35, .15);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1, 1);
            SoulNetwork.showSacrificeTotem(player);
            callback.setReturnValue(true);
            return;
        }
    }
}
